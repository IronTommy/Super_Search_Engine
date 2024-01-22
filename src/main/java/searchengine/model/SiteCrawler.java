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
import searchengine.services.IndexingException;
import searchengine.services.PageService;
import searchengine.services.SiteService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SiteCrawler {

    @Value("${app.userAgent}")
    private String userAgent;

    @Value("${app.referer}")
    private String referer;

    private static final Logger logger = LoggerFactory.getLogger(SiteCrawler.class);

    private final AtomicBoolean stopCrawling = new AtomicBoolean(false);

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteService siteService;
    private final PageService pageService;

    @Autowired
    public SiteCrawler(SiteRepository siteRepository, PageRepository pageRepository, SiteService siteService, PageService pageService) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.siteService = siteService;
        this.pageService = pageService;
    }

    public void crawlSite(SiteConfig siteConfig) throws IndexingException {
        try {
            Site site = siteService.createOrUpdateSite(siteConfig);
            crawlPages(site, siteConfig.getUrl());
        } catch (Exception e) {
            logger.error("Ошибка при индексации сайта: {}", siteConfig.getUrl(), e);
            throw new IndexingException("Ошибка при индексации сайта: " + siteConfig.getUrl(), e);
        } finally {
            stopCrawling.set(false);
        }
    }

    private void crawlPages(Site site, String siteUrl) throws IndexingException {
        try {
            Document document = Jsoup.connect(siteUrl)
                    .userAgent(userAgent)
                    .referrer(referer)
                    .followRedirects(true)
                    .get();

            processPage(site, siteUrl, "/", document);

            Elements links = document.select("a[href]");
            for (Element link : links) {

                if (stopCrawling.get()) {
                    logger.info("Индексация прервана: {}", siteUrl);
                    return;
                }

                String href = link.attr("href");
                if (!href.startsWith("http")) {
                    href = siteUrl + href;
                }

                // Ожидание перед каждым запросом
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                crawlPage(site, siteUrl, href);
            }
        } catch (IOException e) {
            logger.error("Ошибка при обходе сайта: " + siteUrl, e);
            throw new IndexingException("Ошибка при обходе сайта: " + siteUrl, e);
        }
    }

    private void crawlPage(Site site, String siteUrl, String href) throws IndexingException {
        if (stopCrawling.get()) {
            logger.info("Индексация прервана: {}", siteUrl);
            return;
        }

        try {
            // Отделяем номер телефона от URL
            String[] parts = href.split(":");
            if (parts.length > 1) {
                href = parts[0] + ":" + parts[1];
            }

            Document pageDocument = Jsoup.connect(href)
                    .userAgent(userAgent)
                    .referrer(referer)
                    .get();

            processPage(site, siteUrl, href, pageDocument);
        } catch (IOException e) {
            logger.error("Ошибка при обходе страницы {} на сайте: {}", href, siteUrl, e);
            throw new IndexingException("Ошибка при обходе страницы " + href + " на сайте " + siteUrl, e);
        }
    }

    private void processPage(Site site, String siteUrl, String path, Document document) throws IndexingException {
        try {
            String content = document.html();
            pageService.createOrUpdatePage(site, siteUrl, path, 200, content);
            logger.info("Страница успешно обработана: {}{}", site.getUrl(), path);
        } catch (Exception e) {
            logger.error("Ошибка при обработке страницы {} на сайте: {}", path, site.getUrl(), e);
            throw new IndexingException("Ошибка при обработке страницы " + path + " на сайте " + site.getUrl(), e);
        }
    }

    public void stopCrawling() {
        this.stopCrawling.set(true);
    }
}
