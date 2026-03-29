package com.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableCaching      // Activates @Cacheable / @CacheEvict annotations
@EnableScheduling   // Activates @Scheduled annotation for periodic tasks
public class RlaasApplication {
    public static void main(String[] args) {
        SpringApplication.run(RlaasApplication.class, args);
    }
}