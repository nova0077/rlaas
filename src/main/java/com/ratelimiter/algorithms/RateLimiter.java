package com.ratelimiter.algorithms;

/**
 * Interface for rate limiters.
 * 
 * This interface defines the contract for rate limiters.
 * Rate limiters are used to limit the rate of requests to a resource.
 */
public interface RateLimiter {

    /**
     * Check if a request is allowed for the given key.
     * 
     * @param key unique identifier for the request
     * @return RateLimitResult containing the result of the check
     */
    RateLimitResult check(String key);

    /**
     * Return the name of the algorithm used by the rate limiter.
     */
    String getAlgorithmName();

    /**
     * Reset the rate limit state for a given key
     * 
     * @param key The key to reset
     */
    void reset(String key);

    /**
     * Get current state/ configuration of a key
     */
    String getState(String key);

    /**
     * Clean up expired entries to prevent memory leaks.
     * Should be called periodically in production.
     */
    void cleanup();
}