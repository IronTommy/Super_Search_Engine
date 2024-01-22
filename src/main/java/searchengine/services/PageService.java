package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;

import java.util.Optional;

@Service
public class PageService {

    private static final Logger logger = LoggerFactory.getLogger(PageService.class);

    private final PageRepository pageRepository;

    @Autowired
    public PageService(PageRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    @Transactional
    public Page createOrUpdatePage(Site site, String siteUrl, String path, int code, String content) throws IndexingException {
        try {
            Optional<Page> existingPageOptional = pageRepository.findBySiteAndPath(site, path);

            if (existingPageOptional.isPresent()) {
                Page page = existingPageOptional.get();
                return updatePageFields(page, code, content);
            } else {
                return createNewPage(site, path, code, content);
            }
        } catch (Exception e) {
            // Логирование ошибки
            logger.error("Error while creating or updating page", e);
            throw new IndexingException("Error while creating or updating page", e);
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
            // Логирование ошибки
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
            // Логирование ошибки
            logger.error("Error while updating page fields", e);
            throw new IndexingException("Error while updating page fields", e);
        }
    }
}
