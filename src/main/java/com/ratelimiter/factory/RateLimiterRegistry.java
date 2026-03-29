package com.ratelimiter.factory;

import com.ratelimiter.algorithms.*;
import com.ratelimiter.algorithms.redis.*;
import com.ratelimiter.model.entity.RateLimitPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * RateLimiterRegistry — The instantiation engine for our algorithms.
 *
 * This class does two things:
 * 1. FACTORY: Reads a RateLimitPolicy and instantiates the correct
 * `RateLimiter` implementation.
 * 2. REGISTRY: Caches that instance in a ConcurrentHashMap keyed by the policy
 * ID
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterRegistry {

    private final StringRedisTemplate redisTemplate;

    // Cache of instantiated limiters. Key = Policy ID, Value = RateLimiter instance
    private final Map<Long, RateLimiter> limiters = new ConcurrentHashMap<>();

    /**
     * Get or create a RateLimiter for a given policy.
     * 
     * @param policy The policy configuration
     * @return An active RateLimiter instance configured for this policy
     */
    public RateLimiter getLimiter(RateLimitPolicy policy) {
        // computeIfAbsent is atomic. If two threads request it simultaneously,
        // it only creates the object once.
        return limiters.computeIfAbsent(policy.getId(), id -> createLimiter(policy));
    }

    /**
     * Force recreates a limiter. Used when a policy is updated via the Admin API.
     */
    public void evictLimiter(Long policyId) {
        limiters.remove(policyId);
        log.info("Evicted limiter for policy id: {}", policyId);
    }

    /**
     * Factory method to instantiate the concrete algorithm class based on the
     * policy fields.
     */
    private RateLimiter createLimiter(RateLimitPolicy policy) {
        log.info("Instantiating new Rate Limiter for policy: {} (Algorithm: {}, Redis: {})",
                policy.getName(), policy.getAlgorithmType(), policy.getUseRedis());

        if (Boolean.TRUE.equals(policy.getUseRedis())) {
            // REDIS BACKED LIMITERS
            switch (policy.getAlgorithmType()) {
                case FIXED_WINDOW:
                    return new RedisFixedWindowRateLimiter(redisTemplate, policy.getWindowSizeMs(),
                            policy.getMaxRequests());
                case SLIDING_WINDOW_LOG:
                    return new RedisSlidingWindowLoggerRateLimiter(redisTemplate, policy.getWindowSizeMs(),
                            policy.getMaxRequests());
                case TOKEN_BUCKET:
                    validateTokenBucketConfig(policy);
                    return new RedisTokenBucketRateLimiter(redisTemplate, policy.getRefillRate(),
                            policy.getBucketSize());
                case LEAKY_BUCKET:
                    validateTokenBucketConfig(policy); // Leaky Bucket uses the same config fields
                    return new RedisLeakyBucketRateLimiter(redisTemplate, policy.getRefillRate(),
                            policy.getBucketSize());
                case SLIDING_WINDOW_COUNTER:
                default:
                    log.warn("Algorithm {} not supported in Redis mode yet. Falling back to Redis Fixed Window.",
                            policy.getAlgorithmType());
                    return new RedisFixedWindowRateLimiter(redisTemplate, policy.getWindowSizeMs(),
                            policy.getMaxRequests());
            }
        } else {
            // IN-MEMORY LIMITERS
            switch (policy.getAlgorithmType()) {
                case FIXED_WINDOW:
                    return new FixedWindowRateLimiter(policy.getWindowSizeMs(), policy.getMaxRequests());
                case SLIDING_WINDOW_LOG:
                    return new SlidingWindowLogRateLimiter(policy.getWindowSizeMs(), policy.getMaxRequests());
                case SLIDING_WINDOW_COUNTER:
                    return new SlidingWindowCounterRateLimiter(policy.getWindowSizeMs(), policy.getMaxRequests());
                case TOKEN_BUCKET:
                    validateTokenBucketConfig(policy);
                    return new TokenBucketRateLimiter(policy.getRefillRate(), policy.getBucketSize());
                case LEAKY_BUCKET:
                    validateTokenBucketConfig(policy);
                    return new LeakyBucketRateLimiter(policy.getRefillRate(), policy.getBucketSize());
                default:
                    throw new IllegalArgumentException("Unknown algorithm type: " + policy.getAlgorithmType());
            }
        }
    }

    /**
     * Safety check: Token/Leaky bucket algorithms require refillRate and bucketSize
     * which are optional in the DB schema (since Fixed Window doesn't need them).
     */
    // NOTE: This validation should be placed when creating a policy
    // TODO: will move this logic in later commits
    private void validateTokenBucketConfig(RateLimitPolicy policy) {
        if (policy.getRefillRate() == null || policy.getBucketSize() == null) {
            throw new IllegalArgumentException(String.format(
                    "Policy '%s' uses %s but is missing refillRate or bucketSize configuration.",
                    policy.getName(), policy.getAlgorithmType()));
        }
    }
}
