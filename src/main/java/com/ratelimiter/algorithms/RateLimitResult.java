package com.ratelimiter.algorithms;

/**
 * RateLimitResult is a "Value Object" — an immutable object that carries data.
 * It has no identity (we don't persist it), only values.
 *
 * WHY static factory methods (allowed/rejected) instead of constructors?
 * Static factory method = controlled wrapper over constructor
 * READ MORE: Effective Java Item 1 — "Consider static factory methods over
 * constructors"
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * HTTP Headers Context:
 * ─────────────────────────────────────────────────────────────────────────────
 * This class is designed to map directly to standard rate-limit HTTP headers:
 * X-RateLimit-Limit ← limit (maxRequests)
 * X-RateLimit-Remaining ← remaining
 * X-RateLimit-Reset ← resetAt (epoch seconds, per RFC standard)
 * Retry-After ← retryAfterMs / 1000 (seconds)
 */
public class RateLimitResult {

    /** Was the request allowed through? */
    private final boolean allowed;

    /**
     * Maximum requests permitted in the window (X-RateLimit-Limit).
     * This is the POLICY limit, not a counter.
     */
    private final int limit;

    /**
     * Remaining requests before hitting the limit (X-RateLimit-Remaining).
     * For rejected requests, this is always 0.
     */
    private final int remaining;

    /**
     * Unix epoch milliseconds when the current window RESETS or fills up.
     * (X-RateLimit-Reset — typically converted to epoch seconds in HTTP headers)
     */
    private final long resetAtMs;

    /**
     * How many ms the client should WAIT before retrying (Retry-After header).
     * Only meaningful when allowed=false. For allowed results, this is 0.
     */
    private final long retryAfterMs;

    /**
     * Which algorithm produced this result (for observability/debugging).
     * Included in API response headers so clients know what policy is active.
     */
    private final String algorithmName;

    /**
     * Human-readable message for API responses.
     */
    private final String message;

    // Private constructor so callers MUST use the factory methods below.
    private RateLimitResult(boolean allowed, int limit, int remaining,
            long resetAtMs, long retryAfterMs,
            String algorithmName, String message) {
        this.allowed = allowed;
        this.limit = limit;
        this.remaining = remaining;
        this.resetAtMs = resetAtMs;
        this.retryAfterMs = retryAfterMs;
        this.algorithmName = algorithmName;
        this.message = message;
    }

    // ── Static Factory Methods ────────────────────────────────────────────────

    /**
     * Create a result for an ALLOWED request.
     *
     * @param limit         the policy max (e.g., 100 req/min)
     * @param remaining     how many requests are left in the current window
     * @param resetAtMs     when the window resets (epoch ms)
     * @param algorithmName name of the algorithm that made the decision
     * @return an immutable allowed result
     */
    public static RateLimitResult allowed(int limit, int remaining,
            long resetAtMs, String algorithmName) {
        return new RateLimitResult(
                /* allowed */ true,
                /* limit */ limit,
                /* remaining */ remaining,
                /* resetAtMs */ resetAtMs,
                /* retryAfter */ 0L, // No retry needed — request was allowed
                /* algorithm */ algorithmName,
                /* message */ "Request allowed");
    }

    /**
     * Create a result for a REJECTED request.
     *
     * @param limit         the policy max
     * @param resetAtMs     when the window resets
     * @param retryAfterMs  how many ms the client should wait before retrying
     * @param algorithmName name of the algorithm
     * @return an immutable rejected result
     */
    public static RateLimitResult rejected(int limit, long resetAtMs,
            long retryAfterMs, String algorithmName) {
        return new RateLimitResult(
                /* allowed */ false,
                /* limit */ limit,
                /* remaining */ 0, // No remaining — that's WHY it was rejected
                /* resetAtMs */ resetAtMs,
                /* retryAfter */ retryAfterMs,
                /* algorithm */ algorithmName,
                /* message */ String.format(
                        "Rate limit exceeded. Try again in %d ms", retryAfterMs));
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    // WHY manual getters instead of Lombok @Data here?
    // This class is in the `algorithms` package (no Spring/Lombok dependency).
    // Keeping it pure Java ensures it stays framework-agnostic.

    public boolean isAllowed() {
        return allowed;
    }

    public int getLimit() {
        return limit;
    }

    public int getRemaining() {
        return remaining;
    }

    public long getResetAtMs() {
        return resetAtMs;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format(
                "RateLimitResult{allowed=%s, limit=%d, remaining=%d, resetAt=%d, retryAfter=%dms, algo=%s}",
                allowed, limit, remaining, resetAtMs, retryAfterMs, algorithmName);
    }
}