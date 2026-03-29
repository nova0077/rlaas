package com.ratelimiter.controller;

import com.ratelimiter.algorithms.RateLimitResult;
import com.ratelimiter.algorithms.RateLimiter;
import com.ratelimiter.factory.RateLimiterRegistry;
import com.ratelimiter.model.dto.ApiResponse;
import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.model.enums.KeyType;
import com.ratelimiter.service.PolicyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * RateLimitController — Public API for the Rate Limiting Service
 *
 * Note: These API's can be used by any other serivices, by registering their
 * policies.
 */
@RestController
@RequestMapping("/api/v1/ratelimit")
@RequiredArgsConstructor
public class RateLimitController {

    private final PolicyService policyService;
    private final RateLimiterRegistry registry;

    /**
     * DTO for incoming check requests.
     */
    @Data
    public static class CheckRequest {
        private KeyType keyType;
        private String key;
    }

    /**
     * POST /api/v1/ratelimit/check
     * Explicitly check if a request should be allowed.
     */
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<RateLimitResult>> checkLimit(@RequestBody CheckRequest request) {

        Optional<RateLimitPolicy> policyOpt = policyService.findMatchingPolicy(request.getKeyType(), request.getKey());

        if (policyOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No policy applies; request allowed."));
        }

        RateLimiter limiter = registry.getLimiter(policyOpt.get());
        String namespacedKey = request.getKeyType().name() + ":" + request.getKey();

        RateLimitResult result = limiter.check(namespacedKey);

        if (!result.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(result.getRetryAfterMs()))
                    .header("X-RateLimit-Remaining", String.valueOf(result.getRemaining()))
                    .header("X-RateLimit-Limit", String.valueOf(result.getLimit()))
                    .body(ApiResponse.error("Rate limit exceeded", result));
        }

        return ResponseEntity.ok(ApiResponse.success("Request allowed", result));
    }

    /**
     * GET /api/v1/ratelimit/status?keyType=USER&key=premium_123
     * 
     * Get the current state (count, tokens, etc) of a specific key
     * WITHOUT consuming a request/token.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<String>> getStatus(
            @RequestParam KeyType keyType,
            @RequestParam String key) {

        Optional<RateLimitPolicy> policyOpt = policyService.findMatchingPolicy(keyType, key);

        if (policyOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No policy applies for this key."));
        }

        RateLimiter limiter = registry.getLimiter(policyOpt.get());
        String namespacedKey = keyType.name() + ":" + key;

        String state = limiter.getState(namespacedKey);

        return ResponseEntity.ok(ApiResponse.success("Current state retrieved", state));
    }

    /**
     * DELETE /api/v1/ratelimit/reset?keyType=USER&key=premium_123
     * 
     * Reset the rate limit state for a specific key.
     */
    @DeleteMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetLimit(
            @RequestParam KeyType keyType,
            @RequestParam String key) {

        Optional<RateLimitPolicy> policyOpt = policyService.findMatchingPolicy(keyType, key);

        if (policyOpt.isPresent()) {
            RateLimiter limiter = registry.getLimiter(policyOpt.get());
            String namespacedKey = keyType.name() + ":" + key;
            limiter.reset(namespacedKey);
            return ResponseEntity.ok(ApiResponse.success("Rate limit reset successfully"));
        }

        return ResponseEntity.ok(ApiResponse.success("No policy applied, nothing to reset."));
    }
}
