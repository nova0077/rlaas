package com.ratelimiter.algorithms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FixedWindowRateLimiter.
 * These are pure unit tests — no Spring context, no Redis, no DB.
 */
class FixedWindowRateLimiterTest {

    private FixedWindowRateLimiter limiter;
    private static final long WINDOW_SIZE_MS = 10_000; // 10 seconds
    private static final int MAX_REQUESTS = 5;

    @BeforeEach
    void setUp() {
        limiter = new FixedWindowRateLimiter(WINDOW_SIZE_MS, MAX_REQUESTS);
    }

    @Test
    @DisplayName("Should allow requests within the limit")
    void shouldAllowRequestsWithinLimit() {
        String key = "user:1";

        for (int i = 0; i < MAX_REQUESTS; i++) {
            RateLimitResult result = limiter.check(key);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
            assertEquals(MAX_REQUESTS, result.getLimit());
        }
    }

    @Test
    @DisplayName("Should reject requests exceeding the limit")
    void shouldRejectRequestsExceedingLimit() {
        String key = "user:2";

        // Exhaust all allowed requests
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.check(key);
        }

        // Next request should be rejected
        RateLimitResult result = limiter.check(key);
        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemaining());
        assertTrue(result.getRetryAfterMs() > 0, "retryAfterMs should be positive");
    }

    @Test
    @DisplayName("Should track remaining count correctly")
    void shouldTrackRemainingCountCorrectly() {
        String key = "user:3";

        for (int i = 0; i < MAX_REQUESTS; i++) {
            RateLimitResult result = limiter.check(key);
            assertEquals(MAX_REQUESTS - (i + 1), result.getRemaining(),
                    "After request " + (i + 1) + ", remaining should be " + (MAX_REQUESTS - (i + 1)));
        }
    }

    @Test
    @DisplayName("Should isolate different keys")
    void shouldIsolateDifferentKeys() {
        String key1 = "user:A";
        String key2 = "user:B";

        // Exhaust key1
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.check(key1);
        }

        // key2 should still be allowed
        RateLimitResult result = limiter.check(key2);
        assertTrue(result.isAllowed());
        assertEquals(MAX_REQUESTS - 1, result.getRemaining());
    }

    @Test
    @DisplayName("Should reset state for a key")
    void shouldResetStateForKey() {
        String key = "user:reset";

        // Exhaust the limit
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.check(key);
        }
        assertFalse(limiter.check(key).isAllowed());

        // Reset
        limiter.reset(key);

        // Should be allowed again
        RateLimitResult result = limiter.check(key);
        assertTrue(result.isAllowed());
        assertEquals(MAX_REQUESTS - 1, result.getRemaining());
    }

    @Test
    @DisplayName("Should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
        assertEquals("Fixed Window", limiter.getAlgorithmName());
    }

    @Test
    @DisplayName("Should return state information")
    void shouldReturnStateInformation() {
        String key = "user:state";
        limiter.check(key);

        String state = limiter.getState(key);
        assertNotNull(state);
        assertTrue(state.contains("Fixed Window"));
        assertTrue(state.contains(String.valueOf(MAX_REQUESTS)));
    }

    @Test
    @DisplayName("Should allow requests after window rolls over")
    void shouldAllowAfterWindowRollover() throws InterruptedException {
        // Use a very short window for this test
        FixedWindowRateLimiter shortLimiter = new FixedWindowRateLimiter(500, 2);
        String key = "user:rollover";

        // Exhaust
        shortLimiter.check(key);
        shortLimiter.check(key);
        assertFalse(shortLimiter.check(key).isAllowed());

        // Wait for window to roll over
        Thread.sleep(600);

        // Should be allowed again
        assertTrue(shortLimiter.check(key).isAllowed());
    }

    @Test
    @DisplayName("Cleanup should not remove active windows")
    void cleanupShouldNotRemoveActiveWindows() {
        String key = "user:cleanup";
        limiter.check(key);

        limiter.cleanup(); // Should not remove current window

        String state = limiter.getState(key);
        assertTrue(state.contains("Current Count: 1"));
    }
}
