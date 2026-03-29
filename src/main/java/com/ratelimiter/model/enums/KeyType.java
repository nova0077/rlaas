package com.ratelimiter.model.enums;

/**
 * KeyType — What dimension does a rate limit policy apply to?
 *
 * In a real API Gateway, you want different limiting strategies:
 *   - Ban a single abusive user   → per USER
 *   - Protect endpoints from DDoS → per IP
 *   - Throttle expensive APIs     → per API path
 *
 * This enum lets policies be targeted precisely.
 *
 * COMPOSITION EXAMPLE:
 * You might have ALL THREE active simultaneously:
 *   - USER "premium_user" → 10,000 req/hour
 *   - IP  "1.2.3.4"       → 1,000 req/hour (block scrapers)
 *   - API "/api/search"   → 500 req/min (protect expensive endpoint)
 *
 * The rate limiter checks them in order and rejects if ANY limit is exceeded.
 * This is exactly how AWS API Gateway works!
 */
public enum KeyType {

    /**
     * Per-User rate limiting.
     * Key is extracted from the "X-User-Id" request header.
     *
     * Use case: SaaS plans — Basic users get 100 req/hr, Premium get 10,000.
     * Requires authentication middleware to set the X-User-Id header.
     *
     * IMPORTANT: If a request has no X-User-Id header, we fall through to IP-based limiting.
     */
    USER,

    /**
     * Per-IP rate limiting.
     * Key is the client's IP address from the request or X-Forwarded-For header.
     *
     * X-Forwarded-For header: When your app is behind a reverse proxy (NGINX, AWS ALB),
     * the direct IP is the proxy's IP, not the client's. X-Forwarded-For carries the
     * original client IP. Always trust this header only from known proxies!
     *
     * Use case: Prevent DDoS, block scrapers, geo-throttling.
     * Risk: NAT — multiple users behind the same IP share the limit.
     */
    IP,

    /**
     * Per-API-path rate limiting.
     * Key is the request URI path (e.g., "/api/search", "/api/checkout").
     *
     * Use case: Protect expensive operations (LLM inference, DB-heavy queries).
     * Independent of who the user is — the endpoint itself has a global cap.
     * This prevents one popular endpoint from taking down the whole system.
     */
    API
}
