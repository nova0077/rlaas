package com.ratelimiter.algorithms;

/**
 * Result of a rate limit check operation.
 * 
 * This class contains all the information about whether a request
 * was allowed or rejected, and metadata for HTTP headers.
 */
public class RateLimitResult {
    private final boolean isAllowed;
    private final int limit;
    private final int remainingTokens;
    private final long retryAfter;
    private final long resetAt;
    private final String algorithmName;

    public RateLimitResult(boolean isAllowed, int limit, int remainingTokens,
                           long retryAfter, long resetAt, String algorithmName) {
        this.isAllowed = isAllowed;
        this.limit = limit;
        this.remainingTokens = remainingTokens;
        this.retryAfter = retryAfter;
        this.resetAt = resetAt;
        this.algorithmName = algorithmName;
    }

    public boolean isAllowed() {
        return isAllowed;
    }

    public int getLimit() {
        return limit;
    }
    
    public int getRemainingTokens() {
        return remainingTokens;
    }

    public long getRetryAfterMillis() {
        return retryAfter;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    /**
     * Create a result for an allowed request
     */
    public static RateLimitResult allowed(int limit, int remainingTokens, long resetAt, String algorithmName) {
        return new RateLimitResult(true, limit, remainingTokens, 0, resetAt, algorithmName);
    }

    /**
     * Create a result for a rejected request
     */
    public static RateLimitResult rejected(int limit, long resetAt, long retryAfter, String algorithmName) {
        return new RateLimitResult(false, limit, 0, retryAfter, 0, algorithmName);
    }
    
    @Override
    public String toString() {
        if(isAllowed) {
            return String.format("[%s] Allowed | Remaining Tokens: %d/%d | Reset in: %dms", algorithmName, remainingTokens, limit, resetAt);
        } else {
            return String.format("[%s] Rejected | Limit: %d | Retry after: %dms", algorithmName, limit, retryAfter);
        }
    }

    /**
     * Get HTTP-style headers for rate limiting
     */
    public String getHeaders() {
        return String.format(
            "X-RateLimit-Limit: %d\n" +
            "X-RateLimit-Remaining: %d\n" +
            "X-RateLimit-Reset: %d\n" + 
            (retryAfter > 0 ? "Retry-After: %.1f\n" : ""),
            limit, remainingTokens, resetAt/1000,
            retryAfter/1000.0
        );
    }
}