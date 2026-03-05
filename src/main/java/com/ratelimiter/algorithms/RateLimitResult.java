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
    private final long retryAfterMillis;
    private final long resetAtMillis;

    public RateLimitResult(boolean isAllowed, int limit, int remainingTokens,
                           long retryAfterMillis, long resetAtMillis, String algorithmName) {
        this.isAllowed = isAllowed;
        this.limit = limit;
        this.remainingTokens = remainingTokens;
        this.retryAfterMillis = retryAfterMillis;
        this.resetAtMillis = resetAtMillis;
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
        return retryAfterMillis;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    /**
     * Create a result for an allowed request
     */
    public static RateLimitResult allowed(int limit, int remainingTokens, long resetAtMillis, String algorithmName) {
        return new RateLimitResult(true, limit, remainingTokens, 0, resetAtMillis, algorithmName);
    }

    /**
     * Create a result for a rejected request
     */
    public static RateLimitResult rejected(int limit, int remainingTokens, long retryAfterMillis, String algorithmName) {
        return new RateLimitResult(false, limit, remainingTokens, retryAfterMillis, 0, algorithmName);
    }
    
    @Override
    public String toString() {
        if(allowed) {
            return String.format("[%s] Allowed | Remaining Tokens: %d/%d | Reset in: %dms", algorithmName, remainingTokens, limit, resetAtMillis);
        } else {
            return String.format("[%s] Rejected | Limit: %d | Retry after: %dms", algorithmName, limit, retryAfterMillis);
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
            (retryAfterMillis > 0 ? "Retry-After: %.1f\n" : ""),
            limit, remainingTokens, resetAtMillis/1000,
            retryAfterMillis/1000.0
        );
    }
}