package com.ratelimiter.config;

import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.model.enums.AlgorithmType;
import com.ratelimiter.model.enums.KeyType;
import com.ratelimiter.repository.PolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * PreloadDataConfig — Seeds default policies into the database on startup.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY @Configuration?
 * ─────────────────────────────────────────────────────────────────────────────
 * Marks this class as a source of bean definitions for the application context.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT IS a CommandLineRunner?
 * ─────────────────────────────────────────────────────────────────────────────
 * Any Spring Bean that implements CommandLineRunner will have its `run()` method
 * executed EXACTLY ONCE after the entire Spring application has started up.
 * 
 * It's perfect for:
 *   - Seeding database data
 *   - Creating initial directories
 *   - Printing startup banners
 *
 * By returning a CommandLineRunner from a @Bean method, Spring executes it automatically.
 */
@Slf4j
@Configuration
public class PreloadDataConfig {

    @Bean
    CommandLineRunner initDatabase(PolicyRepository repository) {
        return args -> {
            log.info("Checking if database needs preloading...");
            
            if (repository.count() == 0) {
                log.info("Database is empty. Seeding default rate limit policies.");

                RateLimitPolicy globalIpPolicy = RateLimitPolicy.builder()
                        .name("global-ip-limit")
                        .keyType(KeyType.IP)
                        .keyPattern("*") // Matches ANY IP
                        .algorithmType(AlgorithmType.TOKEN_BUCKET)
                        .maxRequests(10)
                        .windowSizeMs(10000L) // Not used by TB strictly, but required non-null
                        .refillRate(1.0)      // 1 request per second
                        .bucketSize(10)       // Burst up to 10
                        .useRedis(false)      // In-memory for learning
                        .enabled(true)
                        .description("Default global rate limit for all IPs to prevent abuse")
                        .build();

                RateLimitPolicy premiumUserPolicy = RateLimitPolicy.builder()
                        .name("premium-users")
                        .keyType(KeyType.USER)
                        .keyPattern("premium_*") // Matches userId=premium_123
                        .algorithmType(AlgorithmType.FIXED_WINDOW)
                        .maxRequests(1000)
                        .windowSizeMs(60000L) // 1 minute
                        .useRedis(false)
                        .enabled(true)
                        .description("High limits for premium users")
                        .build();

                RateLimitPolicy expensiveApiPolicy = RateLimitPolicy.builder()
                        .name("expensive-api-throttle")
                        .keyType(KeyType.API)
                        .keyPattern("/api/v1/expensive-report") // Exact match
                        .algorithmType(AlgorithmType.LEAKY_BUCKET)
                        .maxRequests(5)
                        .windowSizeMs(60000L) // Required non-null
                        .refillRate(0.5)      // Process 1 report every 2 seconds
                        .bucketSize(5)        // Queue max 5 requests
                        .useRedis(false)
                        .enabled(true)
                        .description("Smooth out traffic to the expensive reporting endpoint")
                        .build();

                repository.saveAll(List.of(globalIpPolicy, premiumUserPolicy, expensiveApiPolicy));
                log.info("Successfully seeded 3 default policies.");
            } else {
                log.info("Database already contains policies. Skipping preload.");
            }
        };
    }
}
