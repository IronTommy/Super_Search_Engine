package searchengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import searchengine.services.TextProcessingService;
import searchengine.services.TextProcessingServiceImpl;

import java.io.IOException;
import java.util.concurrent.ForkJoinPool;

@Configuration
public class AppConfig {

    @Bean
    public ForkJoinPool forkJoinPool() {
        return new ForkJoinPool();
    }


    @Bean
    public TextProcessingService textProcessingService() throws IOException {
        return new TextProcessingServiceImpl();
    }
}
