package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.SiteCrawler;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
public class ThreadManagerImpl implements ThreadManager {

    private static final Logger logger = LoggerFactory.getLogger(SiteCrawler.class);
    private final ForkJoinPool forkJoinPool;

    @Autowired
    public ThreadManagerImpl() {
        this.forkJoinPool = ForkJoinPool.commonPool();
    }


    @Override
    public void startThread(Runnable task) {
        logger.info("Starting a new thread");
        forkJoinPool.execute(task);
    }

    @Override
    public void shutdown() {
        forkJoinPool.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return forkJoinPool.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return forkJoinPool.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return forkJoinPool.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return forkJoinPool.awaitTermination(timeout, unit);
    }
}
