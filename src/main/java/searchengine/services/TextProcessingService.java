package searchengine.services;

import java.util.Map;

public interface TextProcessingService {

    Map<String, Integer> collectLemmas(String text);

}
