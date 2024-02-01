package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repository.SiteRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


@Service
public class IndexingServiceImpl implements IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingServiceImpl.class);

    private final SiteRepository siteRepository;

    private final SiteCrawler siteCrawler;
    private final SitesList sitesList;

    private final SiteService siteService;
    private final PageService pageService;

    private final ThreadManager threadManager;
    private final DatabaseService databaseService;

    private final TextProcessingService textProcessingService;

    private final LemmaService lemmaService;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository,
                               SiteCrawler siteCrawler,
                               SitesList sitesList,
                               SiteService siteService,
                               PageService pageService,
                               ThreadManager threadManager,
                               DatabaseService databaseService,
                               TextProcessingService textProcessingService,
                               LemmaService lemmaService
    ) {
        this.siteRepository = siteRepository;
        this.siteCrawler = siteCrawler;
        this.sitesList = sitesList;
        this.siteService = siteService;
        this.pageService = pageService;
        this.threadManager = threadManager;
        this.databaseService = databaseService;
        this.textProcessingService = textProcessingService;
        this.lemmaService = lemmaService;
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
                Optional<Site> existingSiteOptional = databaseService
                        .getExistingSiteByUrl(siteConfig.getUrl());

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

    @Override
    public void indexPage(String url) throws IndexingException {
        try {
            // Шаг 1: Получение HTML-кода страницы
            String htmlContent = siteCrawler.fetchHtmlContent(url);

            // Шаг 2: Сохранение HTML-кода в таблицу page
            Page page = pageService.createPage(url, htmlContent);

            // Получаем сайт по URL
            Site site = siteService.getSiteByUrl(url);

            // Шаг 3: Преобразование HTML-кода в леммы и их количества
            Map<String, Integer> lemmas = textProcessingService.collectLemmas(htmlContent);

            // Шаг 4: Сохранение лемм в таблицу lemma и обновление frequency
            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaText = entry.getKey();
                Integer frequency = entry.getValue();

                // Получаем лемму по тексту
                logger.debug("Trying to get or create lemma for text: {}", lemmaText);
                Lemma lemma = lemmaService.getOrCreateLemma(lemmaText, site);
                logger.debug("Lemma found or created successfully: {}", lemma);

                // Обновляем частоту леммы
                lemma.setFrequency(lemma.getFrequency() + frequency);
                lemmaService.saveLemma(lemma);

                // Обновляем связь леммы и страницы
                pageService.updatePageLemmaRelation(page, lemma, frequency);
            }

        } catch (Exception e) {
            logger.error("Error indexing page: " + url, e);
            throw new IndexingException("Error indexing page: " + url, e);
        }
    }


}