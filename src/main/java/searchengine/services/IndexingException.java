package searchengine.services;

public class IndexingException extends Exception {
    public IndexingException(String message) {
        super(message);
    }

    public IndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
