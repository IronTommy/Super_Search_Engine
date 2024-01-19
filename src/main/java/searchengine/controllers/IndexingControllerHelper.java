package searchengine.controllers;

import org.springframework.stereotype.Component;
import searchengine.services.IndexingException;
import searchengine.services.IndexingService;

import java.util.HashMap;
import java.util.Map;

@Component
public class IndexingControllerHelper {

    private final IndexingService indexingService;

    public IndexingControllerHelper(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    public Map<String, Object> startIndexing() {
        Map<String, Object> response = new HashMap<>();
        try {
            indexingService.startIndexing();
            response.put("result", true);
        } catch (IndexingException e) {
            response.put("result", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    public Map<String, Object> stopIndexing() {
        Map<String, Object> response = new HashMap<>();
        try {
            indexingService.stopIndexing();
            response.put("result", true);
        } catch (IndexingException e) {
            response.put("result", false);
            response.put("error", e.getMessage());
        }
        return response;
    }
}
