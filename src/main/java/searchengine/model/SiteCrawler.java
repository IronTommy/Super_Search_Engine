package searchengine.model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.config.SiteConfig;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SiteCrawler {

    @Value("${app.userAgent}")
    private String userAgent;

    @Value("${app.referer}")
    private String referer;

    private static final Logger logger = LoggerFactory.getLogger(SiteCrawler.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Autowired
    public SiteCrawler(SiteRepository siteRepository, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    public void crawlSite(SiteConfig siteConfig) {
        try {
            String siteUrl = siteConfig.getUrl();
            Site site = new Site();
            site.setUrl(siteUrl);
            site.setStatus(SiteStatus.INDEXING);
            site = siteRepository.save(site);

            try {
                // Перемещаем удаление снаружи блока синхронизации
                siteRepository.deleteByUrl(siteUrl);

                // Логика индексации сайта
                crawlPages(site, siteUrl);

                // По завершении обхода изменение статуса на INDEXED
                site.setStatus(SiteStatus.INDEXED);
            } catch (Exception e) {
                // Если произошла ошибка, изменение статуса на FAILED и запись ошибки
                site.setStatus(SiteStatus.FAILED);
                site.setLastError(e.getMessage());
                logger.error("Ошибка при индексации сайта: {}" + siteUrl, e);
            } finally {
                // Обновляем время статуса и сохраняем изменения
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        } catch (Exception e) {
            // Обработка ошибок, связанных с базой данных или другими операциями
            logger.error("Ошибка при индексации сайта: {}" + siteConfig.getUrl(), e);
        }
    }

    private void crawlPages(Site site, String siteUrl) {
        try {
            Document document = Jsoup.connect(siteUrl)
                    .userAgent(userAgent)
                    .referrer(referer)
                    .followRedirects(true)
                    .get();

            // Обработка главной страницы
            processPage(site, "/", document);

            // Обработка ссылок на другие страницы
            Elements links = document.select("a[href]");
            for (Element link : links) {
                String href = link.attr("href");
                if (!href.startsWith("http")) {
                    href = siteUrl + href;
                }

                if (pageRepository.findBySiteAndPath(site, href).isEmpty()) {
                    try {
                        Document pageDocument = Jsoup.connect(href)
                                .userAgent(userAgent)
                                .referrer(referer)
                                .get();

                        processPage(site, href, pageDocument);
                    } catch (IOException e) {
                        logger.error("Ошибка при обходе страницы {} на сайте: {}", href, siteUrl, e);
                        throw new RuntimeException("Ошибка при обходе страницы", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Ошибка при обходе сайта: " + siteUrl, e);
            throw new RuntimeException("Ошибка при обходе сайта", e);
        }
    }

    private CompletableFuture<Void> crawlPageAsync(Site site, String path, String url) {
        return CompletableFuture.runAsync(() -> {
            try {
                Document pageDocument = Jsoup.connect(url)
                        .userAgent(userAgent)
                        .referrer(referer)
                        .get();

                processPage(site, path, pageDocument);
            } catch (IOException e) {
                logger.error("Ошибка при обходе страницы {} на сайте: {}", path, url, e);
                throw new RuntimeException("Ошибка при обходе страницы", e);
            }
        }, executorService);
    }

    private void processPage(Site site, String path, Document document) {
        try {
            String content = document.html();
            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(200);
            page.setContent(content);
            pageRepository.save(page);
            logger.info("Страница успешно обработана: {}{}", site.getUrl(), path);
        } catch (Exception e) {
            logger.error("Ошибка при обработке страницы {} на сайте: {}", path, site.getUrl(), e);
        }
    }

    private void handleIndexingError(SiteConfig siteConfig, Exception e) {
        Site site = siteRepository.findByUrl(siteConfig.getUrl())
                .orElseThrow(() -> new RuntimeException("Сайт не найден"));

        site.setStatus(SiteStatus.FAILED);
        site.setLastError(e.getMessage());
        siteRepository.save(site);

        logger.error("Ошибка при индексации сайта: {}" + siteConfig.getUrl(), e);
    }

    private void handleCrawlError(String siteUrl, Exception e) {
        logger.error("Ошибка при обходе сайта: {}" + siteUrl, e);
        throw new RuntimeException("Ошибка при обходе сайта", e);
    }
}