package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SiteConfig;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SiteService {

    private static final Logger logger = LoggerFactory.getLogger(SiteService.class);

    private final SiteRepository siteRepository;

    @Autowired
    public SiteService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    @Transactional
    public Site createOrUpdateSite(SiteConfig siteConfig) throws IndexingException {
        try {
            Optional<Site> existingSiteOptional = siteRepository.findByUrl(siteConfig.getUrl());

            if (existingSiteOptional.isPresent()) {
                Site site = existingSiteOptional.get();
                return updateSiteFields(site, siteConfig);
            } else {
                return createNewSite(siteConfig);
            }
        } catch (Exception e) {
            logger.error("Error while creating or updating site", e);
            throw new IndexingException("Error while creating or updating site", e);
        }
    }

    @Transactional
    public Site createNewSite(SiteConfig siteConfig) throws IndexingException {
        try {
            Site newSite = new Site();
            newSite.setName(siteConfig.getName());
            newSite.setUrl(siteConfig.getUrl());
            newSite.setStatus(SiteStatus.INDEXING);
            newSite.setStatusTime(LocalDateTime.now());
            return siteRepository.save(newSite);
        } catch (Exception e) {
            logger.error("Error while creating a new site", e);
            throw new IndexingException("Error while creating a new site", e);
        }
    }

    @Transactional
    public Site updateSiteFields(Site site, SiteConfig siteConfig) throws IndexingException {
        try {
            site.setName(siteConfig.getName());
            site.setStatus(SiteStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());

            if (!site.getUrl().equals(siteConfig.getUrl())) {
                site.setUrl(siteConfig.getUrl());
            }

            return siteRepository.save(site);
        } catch (Exception e) {
            logger.error("Error while updating site fields", e);
            throw new IndexingException("Error while updating site fields", e);
        }
    }
}
