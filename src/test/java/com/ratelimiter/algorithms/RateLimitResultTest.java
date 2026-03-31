package com.ratelimiter.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RateLimitResult value object.
 */
class RateLimitResultTest {

    @Test
    @DisplayName("Allowed result should have correct fields")
    void allowedResultShouldHaveCorrectFields() {
        RateLimitResult result = RateLimitResult.allowed(100, 42, 1700000000L, "Fixed Window");

        assertTrue(result.isAllowed());
        assertEquals(100, result.getLimit());
        assertEquals(42, result.getRemaining());
        assertEquals(1700000000L, result.getResetAtMs());
        assertEquals(0, result.getRetryAfterMs(), "retryAfterMs should be 0 for allowed");
        assertEquals("Fixed Window", result.getAlgorithmName());
        assertEquals("Request allowed", result.getMessage());
    }

    @Test
    @DisplayName("Rejected result should have correct fields")
    void rejectedResultShouldHaveCorrectFields() {
        RateLimitResult result = RateLimitResult.rejected(100, 1700000000L, 5000L, "Token Bucket");

        assertFalse(result.isAllowed());
        assertEquals(100, result.getLimit());
        assertEquals(0, result.getRemaining(), "Remaining should be 0 for rejected");
        assertEquals(1700000000L, result.getResetAtMs());
        assertEquals(5000L, result.getRetryAfterMs());
        assertEquals("Token Bucket", result.getAlgorithmName());
        assertTrue(result.getMessage().contains("5000"), "Message should mention retry time");
    }

    @Test
    @DisplayName("toString should include all fields")
    void toStringShouldIncludeAllFields() {
        RateLimitResult result = RateLimitResult.allowed(50, 25, 999L, "Leaky Bucket");
        String str = result.toString();

        assertTrue(str.contains("allowed=true"));
        assertTrue(str.contains("limit=50"));
        assertTrue(str.contains("remaining=25"));
        assertTrue(str.contains("Leaky Bucket"));
    }
}
