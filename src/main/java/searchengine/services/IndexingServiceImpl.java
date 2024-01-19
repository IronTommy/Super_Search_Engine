package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.SiteCrawler;
import searchengine.model.SiteStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class IndexingServiceImpl implements IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(SiteCrawler.class);

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteCrawler siteCrawler;
    private final SitesList sitesList;

    private final Object indexingLock = new Object();
    private volatile boolean indexingInProgress = false;

    private ExecutorService executorService;
    private List<Future<?>> futures;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository, PageRepository pageRepository, SiteCrawler siteCrawler, SitesList sitesList) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.siteCrawler = siteCrawler;
        this.sitesList = sitesList;
    }

    @Override
    public void startIndexing() throws IndexingException {
        synchronized (indexingLock) {
            if (indexingInProgress) {
                throw new IndexingException("Индексация уже запущена");
            }

            indexingInProgress = true;

            executorService = Executors.newCachedThreadPool();
            futures = new ArrayList<>();

            // Получаем список сайтов из конфигурации
            List<SiteConfig> siteConfigs = sitesList.getSiteConfigs();

            for (SiteConfig siteConfig : siteConfigs) {
                try {
                    logger.info("Начинаем индексацию сайта: {}", siteConfig.getUrl());
                    // Логика индексации одного сайта
                    indexSite(siteConfig);
                    logger.info("Индексация сайта {} завершена успешно", siteConfig.getUrl());
                } catch (Exception e) {
                    // Обработка ошибок индексации
                    logger.error("Ошибка при индексации сайта: {}", siteConfig.getUrl(), e);
                }
            }

            // Ждем завершения всех потоков
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Ошибка при ожидании завершения потока", e);
                }
            });

            executorService.shutdown();

            indexingInProgress = false;
        }
    }

    @Override
    public void stopIndexing() throws IndexingException {
        synchronized (indexingLock) {
            if (!indexingInProgress) {
                throw new IndexingException("Индексация не запущена");
            }

            // Останавливаем все потоки
            futures.forEach(future -> future.cancel(true));

            executorService.shutdownNow();

            indexingInProgress = false;
        }
    }

    private Optional<Site> getExistingSiteByUrl(String url) {
        return siteRepository.findByUrl(url);
    }

    private Site createNewSite(SiteConfig siteConfig) {
        Site newSite = new Site();
        newSite.setName(siteConfig.getName());
        newSite.setUrl(siteConfig.getUrl());
        newSite.setStatus(SiteStatus.INDEXING);
        newSite.setStatusTime(LocalDateTime.now());
        return siteRepository.save(newSite);
    }

    private Site updateSiteFields(Site site, SiteConfig siteConfig) {
        site.setName(siteConfig.getName());
        site.setStatus(SiteStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        // Можете добавить другие поля для обновления, если необходимо
        return siteRepository.save(site);
    }

    private Site performIndexing(Site site, SiteConfig siteConfig) {
        try {
            logger.info("Начинаем индексацию сайта: {}", siteConfig.getUrl());
            siteCrawler.crawlSite(siteConfig);
            logger.info("Индексация сайта {} завершена успешно", siteConfig.getUrl());
            site.setStatus(SiteStatus.INDEXED);
        } catch (Exception e) {
            // Обработка ошибок индексации
            site.setStatus(SiteStatus.FAILED);
            site.setLastError(e.getMessage());
            logger.error("Ошибка при индексации сайта: {}", siteConfig.getUrl(), e);
        }
        return site;
    }

    private void saveSiteWithTimestamp(Site site) {
        // Установка statusTime в любом случае перед сохранением
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    private void indexSite(SiteConfig siteConfig) {
        Optional<Site> existingSiteOptional = getExistingSiteByUrl(siteConfig.getUrl());

        if (existingSiteOptional.isPresent()) {
            Site existingSite = existingSiteOptional.get();
            updateSiteFields(existingSite, siteConfig);
        } else {
            Site newSite = createNewSite(siteConfig);
            existingSiteOptional = Optional.of(newSite);
        }

        Site site = existingSiteOptional.orElseThrow(() -> new RuntimeException("Не удалось получить или создать сущность для сайта: " + siteConfig.getUrl()));
        performIndexing(site, siteConfig);
        saveSiteWithTimestamp(site);
    }

}
