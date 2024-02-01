package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;

import java.util.Optional;

@Service
public class DatabaseServiceImpl implements DatabaseService {

    private final SiteRepository siteRepository;

    @Autowired
    public DatabaseServiceImpl(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    @Override
    public Optional<Site> getExistingSiteByUrl(String url) {
        return siteRepository.findByUrl(url);
    }

}