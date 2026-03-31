package com.ratelimiter.algorithms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SlidingWindowLogRateLimiter.
 *
 * NOTE: This implementation uses a ConcurrentSkipListSet for timestamps,
 * which deduplicates entries at the same millisecond. We use small sleeps
 * between calls to ensure unique timestamps.
 */
class SlidingWindowLogRateLimiterTest {

    private SlidingWindowLogRateLimiter limiter;
    private static final long WINDOW_SIZE_MS = 10_000; // 10 seconds
    private static final int MAX_REQUESTS = 3;

    @BeforeEach
    void setUp() {
        limiter = new SlidingWindowLogRateLimiter(WINDOW_SIZE_MS, MAX_REQUESTS);
    }

    @Test
    @DisplayName("Should allow requests within the limit")
    void shouldAllowRequestsWithinLimit() throws InterruptedException {
        String key = "user:log1";

        for (int i = 0; i < MAX_REQUESTS; i++) {
            RateLimitResult result = limiter.check(key);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
            Thread.sleep(2); // ensure unique timestamps
        }
    }

    @Test
    @DisplayName("Should reject requests exceeding the limit")
    void shouldRejectRequestsExceedingLimit() throws InterruptedException {
        String key = "user:log2";

        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.check(key);
            Thread.sleep(2);
        }

        RateLimitResult result = limiter.check(key);
        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemaining());
    }

    @Test
    @DisplayName("Should track remaining count correctly")
    void shouldTrackRemainingCorrectly() throws InterruptedException {
        String key = "user:log3";

        RateLimitResult first = limiter.check(key);
        assertEquals(MAX_REQUESTS - 1, first.getRemaining());

        Thread.sleep(2);
        RateLimitResult second = limiter.check(key);
        assertEquals(MAX_REQUESTS - 2, second.getRemaining());
    }

    @Test
    @DisplayName("Should isolate different keys")
    void shouldIsolateDifferentKeys() throws InterruptedException {
        String key1 = "user:logA";
        String key2 = "user:logB";

        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.check(key1);
            Thread.sleep(2);
        }
        assertFalse(limiter.check(key1).isAllowed());

        assertTrue(limiter.check(key2).isAllowed());
    }

    @Test
    @DisplayName("Should reset state for a key")
    void shouldResetStateForKey() throws InterruptedException {
        String key = "user:logReset";

        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.check(key);
            Thread.sleep(2);
        }
        assertFalse(limiter.check(key).isAllowed());

        limiter.reset(key);

        assertTrue(limiter.check(key).isAllowed());
    }

    @Test
    @DisplayName("Should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
        assertEquals("Rate Limiter Logger", limiter.getAlgorithmName());
    }

    @Test
    @DisplayName("Should allow after window expires")
    void shouldAllowAfterWindowExpires() throws InterruptedException {
        SlidingWindowLogRateLimiter shortLimiter = new SlidingWindowLogRateLimiter(500, 2);
        String key = "user:expire";

        shortLimiter.check(key);
        Thread.sleep(2);
        shortLimiter.check(key);
        Thread.sleep(2);
        assertFalse(shortLimiter.check(key).isAllowed());

        Thread.sleep(600);

        assertTrue(shortLimiter.check(key).isAllowed());
    }
}
