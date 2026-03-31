package com.ratelimiter.algorithms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenBucketRateLimiter.
 */
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter limiter;
    private static final double REFILL_RATE = 2.0; // 2 tokens per second
    private static final int BUCKET_SIZE = 5;

    @BeforeEach
    void setUp() {
        limiter = new TokenBucketRateLimiter(REFILL_RATE, BUCKET_SIZE);
    }

    @Test
    @DisplayName("Should allow burst up to bucket size")
    void shouldAllowBurstUpToBucketSize() {
        String key = "user:burst";

        // First BUCKET_SIZE requests should all be allowed (burst)
        for (int i = 0; i < BUCKET_SIZE; i++) {
            RateLimitResult result = limiter.check(key);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Should reject after bucket is empty")
    void shouldRejectAfterBucketEmpty() {
        String key = "user:exhaust";

        // Drain all tokens
        for (int i = 0; i < BUCKET_SIZE; i++) {
            limiter.check(key);
        }

        // Next request should be rejected
        RateLimitResult result = limiter.check(key);
        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemaining());
        assertTrue(result.getRetryAfterMs() > 0);
    }

    @Test
    @DisplayName("Should track remaining tokens correctly")
    void shouldTrackRemainingTokensCorrectly() {
        String key = "user:remaining";

        RateLimitResult first = limiter.check(key);
        assertTrue(first.isAllowed());
        assertEquals(BUCKET_SIZE - 1, first.getRemaining());

        RateLimitResult second = limiter.check(key);
        assertTrue(second.isAllowed());
        assertEquals(BUCKET_SIZE - 2, second.getRemaining());
    }

    @Test
    @DisplayName("Should refill tokens over time")
    void shouldRefillTokensOverTime() throws InterruptedException {
        String key = "user:refill";

        // Drain all tokens
        for (int i = 0; i < BUCKET_SIZE; i++) {
            limiter.check(key);
        }
        assertFalse(limiter.check(key).isAllowed());

        // Wait enough time for at least 1 token to refill
        // At 2 tokens/sec, 1 second should give ~2 tokens
        Thread.sleep(1100);

        RateLimitResult result = limiter.check(key);
        assertTrue(result.isAllowed(), "Should be allowed after token refill");
    }

    @Test
    @DisplayName("Should isolate different keys")
    void shouldIsolateDifferentKeys() {
        String key1 = "user:X";
        String key2 = "user:Y";

        // Drain key1
        for (int i = 0; i < BUCKET_SIZE; i++) {
            limiter.check(key1);
        }
        assertFalse(limiter.check(key1).isAllowed());

        // key2 should be fully available
        RateLimitResult result = limiter.check(key2);
        assertTrue(result.isAllowed());
        assertEquals(BUCKET_SIZE - 1, result.getRemaining());
    }

    @Test
    @DisplayName("Should reset bucket for a key")
    void shouldResetBucketForKey() {
        String key = "user:reset";

        // Drain tokens
        for (int i = 0; i < BUCKET_SIZE; i++) {
            limiter.check(key);
        }
        assertFalse(limiter.check(key).isAllowed());

        // Reset
        limiter.reset(key);

        // Should be fully allowed again
        RateLimitResult result = limiter.check(key);
        assertTrue(result.isAllowed());
        assertEquals(BUCKET_SIZE - 1, result.getRemaining());
    }

    @Test
    @DisplayName("Should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
        assertEquals("Token Bucket", limiter.getAlgorithmName());
    }

    @Test
    @DisplayName("Limit field should equal bucket size")
    void limitFieldShouldEqualBucketSize() {
        RateLimitResult result = limiter.check("user:limit");
        assertEquals(BUCKET_SIZE, result.getLimit());
    }
}
