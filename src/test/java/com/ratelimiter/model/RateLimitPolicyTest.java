package com.ratelimiter.model;

import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.model.enums.AlgorithmType;
import com.ratelimiter.model.enums.KeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitPolicy — specifically the matches() method
 * and the Builder pattern.
 */
class RateLimitPolicyTest {

    private RateLimitPolicy buildPolicy(String keyPattern) {
        return RateLimitPolicy.builder()
                .name("test-policy")
                .keyType(KeyType.USER)
                .keyPattern(keyPattern)
                .algorithmType(AlgorithmType.FIXED_WINDOW)
                .maxRequests(100)
                .windowSizeMs(60000L)
                .build();
    }

    // ── matches() — Wildcard "*" ──────────────────────────────────────────────

    @Test
    @DisplayName("Wildcard * should match any key")
    void wildcardShouldMatchAnyKey() {
        RateLimitPolicy policy = buildPolicy("*");

        assertTrue(policy.matches("any-key"));
        assertTrue(policy.matches("user_123"));
        assertTrue(policy.matches("/api/v1/search"));
        assertTrue(policy.matches("192.168.1.1"));
    }

    // ── matches() — Prefix matching ──────────────────────────────────────────

    @Test
    @DisplayName("Prefix pattern should match keys starting with prefix")
    void prefixPatternShouldMatchStartingKeys() {
        RateLimitPolicy policy = buildPolicy("premium_*");

        assertTrue(policy.matches("premium_123"));
        assertTrue(policy.matches("premium_abc"));
        assertFalse(policy.matches("free_123"));
        assertFalse(policy.matches("premium")); // no underscore suffix
    }

    // ── matches() — Suffix matching ──────────────────────────────────────────

    @Test
    @DisplayName("Suffix pattern should match keys ending with suffix")
    void suffixPatternShouldMatchEndingKeys() {
        RateLimitPolicy policy = buildPolicy("*@gmail.com");

        assertTrue(policy.matches("alice@gmail.com"));
        assertTrue(policy.matches("bob@gmail.com"));
        assertFalse(policy.matches("alice@yahoo.com"));
    }

    // ── matches() — Exact matching ───────────────────────────────────────────

    @Test
    @DisplayName("Exact pattern should only match exact key")
    void exactPatternShouldMatchExactKey() {
        RateLimitPolicy policy = buildPolicy("/api/v1/expensive-report");

        assertTrue(policy.matches("/api/v1/expensive-report"));
        assertFalse(policy.matches("/api/v1/expensive-report/detail"));
        assertFalse(policy.matches("/api/v1/cheap-report"));
    }

    // ── matches() — Null safety ──────────────────────────────────────────────

    @Test
    @DisplayName("Should return false for null key")
    void shouldReturnFalseForNullKey() {
        RateLimitPolicy policy = buildPolicy("*");
        assertFalse(policy.matches(null));
    }

    @Test
    @DisplayName("Should return false when keyPattern is null")
    void shouldReturnFalseWhenKeyPatternIsNull() {
        RateLimitPolicy policy = buildPolicy(null);
        assertFalse(policy.matches("some-key"));
    }

    // ── Builder defaults ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Builder should set useRedis=false by default")
    void builderShouldDefaultUseRedisToFalse() {
        RateLimitPolicy policy = buildPolicy("*");
        assertFalse(policy.getUseRedis());
    }

    @Test
    @DisplayName("Builder should set enabled=true by default")
    void builderShouldDefaultEnabledToTrue() {
        RateLimitPolicy policy = buildPolicy("*");
        assertTrue(policy.getEnabled());
    }
}
