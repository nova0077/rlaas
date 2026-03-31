package com.ratelimiter.algorithms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LeakyBucketRateLimiter.
 */
class LeakyBucketRateLimiterTest {

    private LeakyBucketRateLimiter limiter;
    private static final double LEAK_RATE = 2.0; // 2 requests per second drain
    private static final int BUCKET_SIZE = 5;

    @BeforeEach
    void setUp() {
        limiter = new LeakyBucketRateLimiter(LEAK_RATE, BUCKET_SIZE);
    }

    @Test
    @DisplayName("Should allow requests while queue has space")
    void shouldAllowRequestsWhileQueueHasSpace() {
        String key = "user:queue";

        for (int i = 0; i < BUCKET_SIZE; i++) {
            RateLimitResult result = limiter.check(key);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Should reject when queue overflows")
    void shouldRejectWhenQueueOverflows() {
        String key = "user:overflow";

        // Fill the queue
        for (int i = 0; i < BUCKET_SIZE; i++) {
            limiter.check(key);
        }

        // Next request should overflow
        RateLimitResult result = limiter.check(key);
        assertFalse(result.isAllowed());
        assertTrue(result.getRetryAfterMs() > 0);
    }

    @Test
    @DisplayName("Should track remaining space correctly")
    void shouldTrackRemainingSpaceCorrectly() {
        String key = "user:space";

        RateLimitResult first = limiter.check(key);
        assertTrue(first.isAllowed());
        assertEquals(BUCKET_SIZE - 1, first.getRemaining());
    }

    @Test
    @DisplayName("Should allow new requests after leaking")
    void shouldAllowAfterLeaking() throws InterruptedException {
        String key = "user:leak";

        // Fill the queue
        for (int i = 0; i < BUCKET_SIZE; i++) {
            limiter.check(key);
        }
        assertFalse(limiter.check(key).isAllowed());

        // Wait for some requests to leak out
        // At 2 req/sec, 1 second should leak ~2 requests
        Thread.sleep(1100);

        RateLimitResult result = limiter.check(key);
        assertTrue(result.isAllowed(), "Should be allowed after requests leaked out");
    }

    @Test
    @DisplayName("Should isolate different keys")
    void shouldIsolateDifferentKeys() {
        String key1 = "ip:1.1.1.1";
        String key2 = "ip:2.2.2.2";

        // Fill key1
        for (int i = 0; i < BUCKET_SIZE; i++) {
            limiter.check(key1);
        }
        assertFalse(limiter.check(key1).isAllowed());

        // key2 should be fully available
        assertTrue(limiter.check(key2).isAllowed());
    }

    @Test
    @DisplayName("Should reset queue for a key")
    void shouldResetQueueForKey() {
        String key = "user:reset";

        // Fill the queue
        for (int i = 0; i < BUCKET_SIZE; i++) {
            limiter.check(key);
        }
        assertFalse(limiter.check(key).isAllowed());

        limiter.reset(key);

        // Should be allowed again
        assertTrue(limiter.check(key).isAllowed());
    }

    @Test
    @DisplayName("Should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
        assertEquals("Leaky Bucket", limiter.getAlgorithmName());
    }

    @Test
    @DisplayName("Limit field should equal bucket size")
    void limitFieldShouldEqualBucketSize() {
        RateLimitResult result = limiter.check("user:limit");
        assertEquals(BUCKET_SIZE, result.getLimit());
    }
}
