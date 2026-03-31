package com.ratelimiter.algorithms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SlidingWindowCounterRateLimiter.
 */
class SlidingWindowCounterRateLimiterTest {

    private SlidingWindowCounterRateLimiter limiter;
    private static final long WINDOW_SIZE_MS = 10_000;
    private static final int MAX_REQUESTS = 5;

    @BeforeEach
    void setUp() {
        limiter = new SlidingWindowCounterRateLimiter(WINDOW_SIZE_MS, MAX_REQUESTS);
    }

    @Test
    @DisplayName("Should allow requests within the limit")
    void shouldAllowRequestsWithinLimit() {
        String key = "user:sw1";

        for (int i = 0; i < MAX_REQUESTS; i++) {
            RateLimitResult result = limiter.check(key);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Should reject requests when weighted count exceeds the limit")
    void shouldRejectWhenWeightedCountExceedsLimit() {
        String key = "user:sw2";

        // Send more requests than the limit — at some point the weighted
        // count will exceed MAX_REQUESTS
        int rejected = 0;
        for (int i = 0; i < MAX_REQUESTS + 5; i++) {
            RateLimitResult result = limiter.check(key);
            if (!result.isAllowed()) {
                rejected++;
            }
        }
        assertTrue(rejected > 0, "Some requests should have been rejected");
    }

    @Test
    @DisplayName("Should isolate different keys")
    void shouldIsolateDifferentKeys() {
        String key1 = "user:swA";
        String key2 = "user:swB";

        // Exhaust key1
        for (int i = 0; i < MAX_REQUESTS + 3; i++) {
            limiter.check(key1);
        }

        // key2 should still be allowed
        RateLimitResult result = limiter.check(key2);
        assertTrue(result.isAllowed());
    }

    @Test
    @DisplayName("Should reset state for a key")
    void shouldResetStateForKey() {
        String key = "user:swReset";

        // Fill up
        for (int i = 0; i < MAX_REQUESTS + 3; i++) {
            limiter.check(key);
        }

        limiter.reset(key);

        // First request after reset should be allowed
        assertTrue(limiter.check(key).isAllowed());
    }

    @Test
    @DisplayName("Should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
        assertEquals("Sliding Window Counter", limiter.getAlgorithmName());
    }

    @Test
    @DisplayName("Limit field should equal maxRequests")
    void limitFieldShouldEqualMaxRequests() {
        RateLimitResult result = limiter.check("user:swLimit");
        assertEquals(MAX_REQUESTS, result.getLimit());
    }

    @Test
    @DisplayName("Should return state information")
    void shouldReturnStateInformation() {
        String key = "user:swState";
        limiter.check(key);

        String state = limiter.getState(key);
        assertNotNull(state);
        assertTrue(state.contains("Sliding Window Counter"));
        assertTrue(state.contains(String.valueOf(MAX_REQUESTS)));
    }
}
