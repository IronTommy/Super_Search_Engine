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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingServiceImpl implements IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingServiceImpl.class);

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private final SiteCrawler siteCrawler;
    private final SitesList sitesList;

    private final SiteService siteService;
    private final PageService pageService;

    private final ThreadManager threadManager;
    private final DatabaseService databaseService;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository,
                               PageRepository pageRepository,
                               SiteCrawler siteCrawler,
                               SitesList sitesList,
                               SiteService siteService,
                               PageService pageService,
                               ThreadManager threadManager,
                               DatabaseService databaseService
    ) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.siteCrawler = siteCrawler;
        this.sitesList = sitesList;
        this.siteService = siteService;
        this.pageService = pageService;
        this.threadManager = threadManager;
        this.databaseService = databaseService;
    }

    @Override
    public void startIndexing() throws IndexingException {
        if (indexingInProgress.getAndSet(true)) {
            throw new IndexingException("Индексация уже запущена");
        }

        try {
            List<SiteConfig> siteConfigs = sitesList.getSiteConfigs();

            for (SiteConfig siteConfig : siteConfigs) {
                try {
                    logger.info("Начинаем индексацию сайта: {}", siteConfig.getUrl());
                    indexSite(siteConfig);
                    logger.info("Индексация сайта {} завершена успешно", siteConfig.getUrl());
                } catch (Exception e) {
                    throw new IndexingException("Ошибка при индексации сайта: " + siteConfig.getUrl(), e);
                }
            }
        } finally {
            indexingInProgress.set(false);
        }
    }

    @Override
    public void stopIndexing() throws IndexingException {
        logger.info("Остановка индексации");
        siteCrawler.stopCrawling();
        threadManager.shutdownNow();

        try {
            // Ждем завершения потоков в течение 3 секунд
            if (!threadManager.awaitTermination(3, TimeUnit.SECONDS)) {
                logger.warn("Не удалось завершить все потоки в течение 3 секунд. Прерывание оставшихся потоков.");

                // Если потоки не завершились, прерываем оставшиеся потоки мгновенно
                siteCrawler.stopCrawling();
                threadManager.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void performIndexing(Site site, SiteConfig siteConfig) throws IndexingException {
        try {
            logger.info("Начинаем индексацию сайта: {}", siteConfig.getUrl());
            if (!indexingInProgress.compareAndSet(false, true)) {
                logger.info("Индексация прервана: {}", siteConfig.getUrl());
                return;
            }
            siteCrawler.crawlSite(siteConfig);
            logger.info("Индексация сайта {} завершена успешно", siteConfig.getUrl());
            site.setStatus(SiteStatus.INDEXED);
        } catch (Exception e) {
            site.setLastError(e.getMessage());
            logger.error("Ошибка при индексации сайта: {}", siteConfig.getUrl(), e);
            site.setStatus(SiteStatus.FAILED);
            throw new IndexingException("Ошибка при индексации сайта: " + siteConfig.getUrl(), e);
        } finally {
            indexingInProgress.set(false);
        }
    }

    private void indexSite(SiteConfig siteConfig) {
        threadManager.startThread(() -> {
            try {
                Optional<Site> existingSiteOptional = databaseService.getExistingSiteByUrl(siteConfig.getUrl());

                Site site;
                if (existingSiteOptional.isPresent()) {
                    site = existingSiteOptional.get();
                    siteService.updateSiteFields(site, siteConfig);
                } else {
                    site = siteService.createNewSite(siteConfig);
                }
                performIndexing(site, siteConfig);
            } catch (Exception e) {
                logger.error("Ошибка при индексации сайта: {}", siteConfig.getUrl(), e);
                setFailedStatus(siteConfig.getUrl(), e.getMessage());
            }
        });
    }

    private void setFailedStatus(String url, String errorMessage) {
        Optional<Site> existingSiteOptional = getExistingSiteByUrl(url);
        existingSiteOptional.ifPresent(site -> {
            site.setStatus(SiteStatus.FAILED);
            site.setLastError(errorMessage);
            siteRepository.save(site);
        });
    }

    private Optional<Site> getExistingSiteByUrl(String url) {
        return siteRepository.findByUrl(url);
    }

}
