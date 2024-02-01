package searchengine.services;

import searchengine.model.Site;

import java.util.Optional;

public interface DatabaseService {
    Optional<Site> getExistingSiteByUrl(String url);
}
