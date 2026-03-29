package com.ratelimiter.model.enums;

/**
 * AlgorithmType — Enumeration of supported rate limiting algorithms.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY an Enum instead of a String?
 * ─────────────────────────────────────────────────────────────────────────────
 * Using String "FIXED_WINDOW" is fragile — a typo like "FIXED_WINDW" compiles
 * fine but causes a runtime error. An enum constrains the value to only valid
 * options AT COMPILE TIME. Your IDE will also provide autocomplete.
 *
 * In JPA, enums are stored as either:
 *   - @Enumerated(EnumType.ORDINAL) → stores 0, 1, 2... (fragile! order matters)
 *   - @Enumerated(EnumType.STRING)  → stores "FIXED_WINDOW" (preferred!)
 * We always use STRING — if you add a new enum value, ordinals shift.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Each enum value carries a human-readable description for API documentation.
 */
public enum AlgorithmType {

    /**
     * FIXED WINDOW
     * ─────────────
     * Divides time into fixed buckets (e.g., every 60s).
     * Counts requests per bucket. Resets to 0 at bucket boundary.
     *
     * PRO: Simple, O(1) memory per key.
     * CON: Burst at boundary — allows 2x limit if traffic hits end of
     *      one window and start of next immediately after.
     *
     * Analogy: A bouncer who resets their count every hour exactly, regardless
     * of WHEN in the hour you arrived.
     */
    FIXED_WINDOW("Fixed Window — simple counter that resets at fixed time boundaries"),

    /**
     * SLIDING WINDOW LOG
     * ──────────────────
     * Stores a timestamp for every request in a sorted list.
     * On each new request: evict timestamps older than windowSize, count remaining.
     *
     * PRO: Most accurate — true sliding window, no burst at boundaries.
     * CON: O(N) memory — stores every timestamp. High memory for high-traffic keys.
     *
     * Analogy: A doorman with a notebook logging exact entry times for
     * the last 60 minutes, flipping back and crossing out old entries.
     */
    SLIDING_WINDOW_LOG("Sliding Window Log — logs every request timestamp; precise but memory-intensive"),

    /**
     * SLIDING WINDOW COUNTER
     * ───────────────────────
     * Approximation of sliding window using two fixed windows:
     * estimate = prev_window_count * (1 - elapsed_fraction) + current_window_count
     *
     * PRO: O(1) memory like Fixed Window, much more accurate than Fixed Window.
     * CON: Approximate, not exact — but within 1% for uniform traffic.
     *
     * This is what Cloudflare and many production systems use!
     * READ MORE: "A Comprehensive Study of Sliding Window Algorithms" by Kong et al.
     */
    SLIDING_WINDOW_COUNTER("Sliding Window Counter — approximation using two windows; efficient & accurate"),

    /**
     * TOKEN BUCKET
     * ─────────────
     * Tokens refill at a fixed rate (e.g., 10/sec) up to a max capacity.
     * Each request consumes 1 token. Requests wait/fail if no tokens available.
     *
     * PRO: Allows controlled bursting up to bucket capacity.
     * CON: Slightly complex state management.
     *
     * Analogy: A bus that holds 10 people. Every second, 1 new ticket is issued.
     * You can board if you have a ticket, wait if not.
     * Used by: AWS, GCP API quotas
     */
    TOKEN_BUCKET("Token Bucket — tokens refill at fixed rate; allows controlled bursting"),

    /**
     * LEAKY BUCKET
     * ─────────────
     * Requests enter a queue. Queue drains at a fixed rate (leak rate).
     * New requests fail if the queue is full (overflow).
     *
     * PRO: Enforces perfectly smooth output rate — no bursts allowed.
     * CON: No burst tolerance, queue introduces latency.
     *
     * Analogy: A leaky bucket — water (requests) fills from the top at any rate,
     * drips out of the bottom at a fixed rate. If bucket overflows → reject.
     * Used by: Traffic shaping in networking (NGINX rate limiting uses this concept)
     */
    LEAKY_BUCKET("Leaky Bucket — fixed drain rate; enforces smooth traffic, no bursting allowed");

    // A human-readable description for each algorithm
    private final String description;

    AlgorithmType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
