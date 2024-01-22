package searchengine.services;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ThreadManager {
    void startThread(Runnable task);
    void shutdown();
    List<Runnable> shutdownNow();
    boolean isShutdown();
    boolean isTerminated();
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
}
