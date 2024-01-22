package searchengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ForkJoinPool;

@Configuration
public class AppConfig {

    @Bean
    public ForkJoinPool forkJoinPool() {
        return new ForkJoinPool();
    }
}
