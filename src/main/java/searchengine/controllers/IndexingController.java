package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class IndexingController {

    private final IndexingControllerHelper indexingControllerHelper;

    @Autowired
    public IndexingController(IndexingControllerHelper indexingControllerHelper) {
        this.indexingControllerHelper = indexingControllerHelper;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        return ResponseEntity.ok(indexingControllerHelper.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        return ResponseEntity.ok(indexingControllerHelper.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam("url") String url) {
        return ResponseEntity.ok(indexingControllerHelper.indexPage(url));
    }
}
