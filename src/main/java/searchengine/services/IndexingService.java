package searchengine.services;

public interface IndexingService {

    void startIndexing() throws IndexingException;

    void stopIndexing() throws IndexingException;
}
