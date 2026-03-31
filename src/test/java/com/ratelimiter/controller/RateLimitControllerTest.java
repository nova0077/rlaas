package com.ratelimiter.controller;

import com.ratelimiter.algorithms.RateLimitResult;
import com.ratelimiter.algorithms.RateLimiter;
import com.ratelimiter.factory.RateLimiterRegistry;
import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.model.enums.AlgorithmType;
import com.ratelimiter.model.enums.KeyType;
import com.ratelimiter.service.PolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvcTest for RateLimitController.
 */
@WebMvcTest(RateLimitController.class)
class RateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PolicyService policyService;

    @MockBean
    private RateLimiterRegistry registry;

    @MockBean
    private RateLimiter rateLimiter;

    private RateLimitPolicy createPolicy() {
        return RateLimitPolicy.builder()
                .id(1L)
                .name("test-ip-limit")
                .keyType(KeyType.IP)
                .keyPattern("*")
                .algorithmType(AlgorithmType.FIXED_WINDOW)
                .maxRequests(10)
                .windowSizeMs(60000L)
                .enabled(true)
                .useRedis(false)
                .build();
    }

    // ── POST /api/v1/ratelimit/check ─────────────────────────────────────────

    @Test
    @DisplayName("POST /check should return allowed when within limit")
    void checkShouldReturnAllowedWhenWithinLimit() throws Exception {
        RateLimitPolicy policy = createPolicy();
        RateLimitResult allowed = RateLimitResult.allowed(10, 9, System.currentTimeMillis() + 60000, "Fixed Window");

        when(policyService.findMatchingPolicy(KeyType.IP, "192.168.1.1")).thenReturn(Optional.of(policy));
        when(registry.getLimiter(policy)).thenReturn(rateLimiter);
        when(rateLimiter.check(anyString())).thenReturn(allowed);

        String json = """
                {
                    "keyType": "IP",
                    "key": "192.168.1.1"
                }
                """;

        mockMvc.perform(post("/api/v1/ratelimit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.remaining").value(9));
    }

    @Test
    @DisplayName("POST /check should return 429 when rate limit exceeded")
    void checkShouldReturn429WhenExceeded() throws Exception {
        RateLimitPolicy policy = createPolicy();
        RateLimitResult rejected = RateLimitResult.rejected(10, System.currentTimeMillis() + 60000, 5000L, "Fixed Window");

        when(policyService.findMatchingPolicy(KeyType.IP, "192.168.1.1")).thenReturn(Optional.of(policy));
        when(registry.getLimiter(policy)).thenReturn(rateLimiter);
        when(rateLimiter.check(anyString())).thenReturn(rejected);

        String json = """
                {
                    "keyType": "IP",
                    "key": "192.168.1.1"
                }
                """;

        mockMvc.perform(post("/api/v1/ratelimit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.allowed").value(false));
    }

    @Test
    @DisplayName("POST /check should return OK when no policy applies")
    void checkShouldReturnOkWhenNoPolicyApplies() throws Exception {
        when(policyService.findMatchingPolicy(any(), anyString())).thenReturn(Optional.empty());

        String json = """
                {
                    "keyType": "USER",
                    "key": "unknown_user"
                }
                """;

        mockMvc.perform(post("/api/v1/ratelimit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("No policy applies; request allowed."));
    }

    // ── GET /api/v1/ratelimit/status ─────────────────────────────────────────

    @Test
    @DisplayName("GET /status should return current state")
    void statusShouldReturnCurrentState() throws Exception {
        RateLimitPolicy policy = createPolicy();

        when(policyService.findMatchingPolicy(KeyType.IP, "1.2.3.4")).thenReturn(Optional.of(policy));
        when(registry.getLimiter(policy)).thenReturn(rateLimiter);
        when(rateLimiter.getState(anyString())).thenReturn("Current Count: 5");

        mockMvc.perform(get("/api/v1/ratelimit/status")
                        .param("keyType", "IP")
                        .param("key", "1.2.3.4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Current Count: 5"));
    }

    // ── DELETE /api/v1/ratelimit/reset ───────────────────────────────────────

    @Test
    @DisplayName("DELETE /reset should reset rate limit state")
    void resetShouldResetRateLimitState() throws Exception {
        RateLimitPolicy policy = createPolicy();

        when(policyService.findMatchingPolicy(KeyType.IP, "1.2.3.4")).thenReturn(Optional.of(policy));
        when(registry.getLimiter(policy)).thenReturn(rateLimiter);

        mockMvc.perform(delete("/api/v1/ratelimit/reset")
                        .param("keyType", "IP")
                        .param("key", "1.2.3.4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Rate limit reset successfully"));
    }

    @Test
    @DisplayName("DELETE /reset should return OK when no policy applies")
    void resetShouldReturnOkWhenNoPolicyApplies() throws Exception {
        when(policyService.findMatchingPolicy(any(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/ratelimit/reset")
                        .param("keyType", "USER")
                        .param("key", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No policy applied, nothing to reset."));
    }
}
