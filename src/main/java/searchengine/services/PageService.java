package searchengine.services;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;

import java.util.Optional;

@Service
public class PageService {

    private static final Logger logger = LoggerFactory.getLogger(PageService.class);

    private final PageRepository pageRepository;
    private final LemmaService lemmaService;
    private final SiteService siteService;

    @Autowired
    public PageService(PageRepository pageRepository, LemmaService lemmaService, SiteService siteService) {
        this.pageRepository = pageRepository;
        this.lemmaService = lemmaService;
        this.siteService = siteService;
    }

    @Transactional
    public Page createOrUpdatePage(Site site, String siteUrl, String path, int code, String content) throws IndexingException {
        try {
            Optional<Page> existingPageOptional = pageRepository.findBySiteAndPath(site, path);

            if (existingPageOptional.isPresent()) {
                Page page = existingPageOptional.get();
                return updatePageFields(page, code, content);
            } else {
                // Создаем новую страницу
                return createNewPage(site, path, code, content);
            }
        } catch (Exception e) {
            logger.error("Error while creating or updating page", e);
            throw new IndexingException("Error while creating or updating page", e);
        }
    }


    public void updatePageLemmaRelation(Page page, Lemma lemma, int frequency) throws IndexingException {
        try {
            // Обновляем частоту леммы для страницы
            lemmaService.updateLemmaFrequencyForPage(lemma, frequency);
        } catch (Exception e) {
            logger.error("Error while updating page-lemma relation", e);
            throw new IndexingException("Error while updating page-lemma relation", e);
        }
    }

    @Transactional
    public Page createNewPage(Site site, String fullPath, int code, String content) throws IndexingException {
        try {
            Page newPage = new Page();
            newPage.setSite(site);
            newPage.setPath(fullPath);
            newPage.setCode(code);
            newPage.setContent(content);
            return pageRepository.save(newPage);
        } catch (Exception e) {
            logger.error("Error while creating a new page", e);
            throw new IndexingException("Error while creating a new page", e);
        }
    }

    @Transactional
    public Page updatePageFields(Page page, int newCode, String newContent) throws IndexingException {
        try {
            page.setCode(newCode);
            page.setContent(newContent);
            return pageRepository.save(page);
        } catch (Exception e) {
            logger.error("Error while updating page fields", e);
            throw new IndexingException("Error while updating page fields", e);
        }
    }

    @Transactional
    public Page createPage(String url, String htmlContent) {
        // Предположим, что у нас есть объект site и мы его получили
        Site site = siteService.getSiteByUrl(url);
        if (site == null) {
            // Обработка отсутствия сайта
            return null;
        }

        try {
            // Создаем или обновляем страницу
            return createOrUpdatePage(site, url, "path", 200, htmlContent);
        } catch (IndexingException e) {
            logger.error("Error while creating or updating page", e);
            // Обработка ошибки индексации
            return null;
        }
    }



}
