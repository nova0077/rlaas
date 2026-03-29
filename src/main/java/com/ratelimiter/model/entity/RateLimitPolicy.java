package com.ratelimiter.model.entity;

import com.ratelimiter.model.enums.AlgorithmType;
import com.ratelimiter.model.enums.KeyType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * RateLimitPolicy — The heart of the dynamic configuration system.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY a JPA Entity?
 * ─────────────────────────────────────────────────────────────────────────────
 * We want rate limit policies to be:
 * 1. Persistent → survive app restarts
 * 2. Dynamic → changeable via REST API without redeployment
 * 3. Queryable → find "which policy applies to this user?"
 *
 * JPA maps this Java class to a "rate_limit_policies" database table.
 * Hibernate auto-generates the CREATE TABLE SQL. You never write DDL!
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Lombok Annotations Used:
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * @Data → generates getters, setters, equals(), hashCode(), toString()
 * @Builder → generates a Builder: Policy.builder().name("x").build()
 * @NoArgsConstructor → generates Policy() — required by JPA (it creates objects
 *                    via reflection)
 * @AllArgsConstructor → generates Policy(id, name, ...) — required by @Builder
 *
 *                     WHY does JPA require a no-args constructor?
 *                     JPA (Hibernate) creates entity instances via reflection
 *                     using Class.newInstance()
 *                     which calls the no-args constructor. If missing → "No
 *                     default constructor" error.
 *
 *                     ─────────────────────────────────────────────────────────────────────────────
 *                     Bean Validation:
 *                     ─────────────────────────────────────────────────────────────────────────────
 *                     @NotNull, @NotBlank, @Min, @Max come from
 *                     jakarta.validation (JSR-380).
 *                     Spring uses these when you annotate a @RequestBody
 *                     parameter with @Valid.
 *                     They're also enforced at the JPA level via Hibernate
 *                     Validator integration.
 *
 *                     ─────────────────────────────────────────────────────────────────────────────
 *                     Policy Example:
 *                     ─────────────────────────────────────────────────────────────────────────────
 *                     {
 *                     name: "free-tier-api",
 *                     keyType: USER,
 *                     keyPattern: "free_*", ← match any key starting with
 *                     "free_"
 *                     algorithmType: TOKEN_BUCKET,
 *                     maxRequests: 100,
 *                     windowSizeMs: 60000, ← 60 seconds
 *                     useRedis: true, ← distributed (multi-node)
 *                     enabled: true
 *                     }
 */
@Entity
/*
 * @Table: Maps this class to a specific DB table name.
 * Without it, Hibernate uses the class name "RateLimitPolicy" as the table
 * name.
 * Explicit naming avoids surprises when refactoring class names.
 */
@Table(name = "rate_limit_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitPolicy {

    /**
     * Primary Key — Unique identifier for each policy.
     *
     * @Id marks this field as the JPA primary key.
     *
     * @GeneratedValue: Hibernate generates the ID automatically.
     *                  Strategies:
     *                  AUTO → lets JPA choose based on DB dialect
     *                  IDENTITY → uses DB auto-increment (MySQL, PostgreSQL SERIAL)
     *                  SEQUENCE → uses a DB sequence object (more efficient,
     *                  supports batching)
     *                  TABLE → uses a separate ID-tracking table (portable but
     *                  slow)
     *
     *                  GenerationType.IDENTITY is simplest for H2 and works fine
     *                  for learning.
     *                  In production with PostgreSQL, SEQUENCE is preferred for
     *                  performance.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable policy name (e.g., "free-tier-users", "search-api-limit").
     *
     * @NotBlank: fails validation if null, empty, or whitespace-only.
     *            (differs from @NotNull which only checks null; @NotEmpty allows
     *            whitespace)
     *
     * @Column(unique = true): adds a UNIQUE constraint in the DB schema.
     *                Two policies with the same name would confuse policy lookups.
     *
     *                length = 100: maps to VARCHAR(100) in SQL. Without length,
     *                Hibernate defaults to VARCHAR(255).
     */
    @NotBlank(message = "Policy name is required")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * What dimension does this policy target? (USER / IP / API)
     *
     * @Enumerated(EnumType.STRING): stored as "USER", "IP", or "API" in DB.
     * WHY not ORDINAL? If you reorder enum values (e.g. swap USER and IP),
     * ORDINAL values change and existing DB rows suddenly mean something different.
     * STRING is safe and self-documenting in the DB.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false)
    private KeyType keyType;

    /**
     * Pattern for matching incoming request keys.
     *
     * Examples:
     * "*" → match all keys (global default policy)
     * "user_123" → match only this specific user
     * "/api/search*" → match any path under /api/search
     * "192.168.*" → match an IP range (simple prefix match)
     *
     * We do simple prefix/suffix matching in PolicyService.
     * A production system might use glob or regex matching.
     */
    @NotBlank
    @Column(name = "key_pattern", nullable = false)
    private String keyPattern;

    /**
     * Which rate limiting algorithm to use for this policy.
     *
     * This is the "Strategy Pattern" in action:
     * The policy stores WHICH strategy to use; the factory instantiates it.
     * Different users can have different algorithms simultaneously.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm_type", nullable = false)
    private AlgorithmType algorithmType;

    /**
     * Maximum requests allowed in the time window.
     *
     * @Min(1): at least 1 request must be allowed (0 would block everyone
     * permanently)
     * @Max(1_000_000): sanity cap — 1M req/window is effectively unlimited
     *
     * Java integer literal 1_000_000: underscores are allowed for readability (Java
     * 7+).
     */
    @NotNull
    @Min(value = 1, message = "maxRequests must be at least 1")
    @Max(value = 1_000_000, message = "maxRequests cannot exceed 1,000,000")
    @Column(name = "max_requests", nullable = false)
    private Integer maxRequests;

    /**
     * Time window size in milliseconds.
     *
     * Examples:
     * 1000 = 1 second
     * 60000 = 1 minute
     * 3600000 = 1 hour
     *
     * Used by: Fixed Window, Sliding Window algorithms.
     * For Token Bucket / Leaky Bucket, this defines the refill period.
     */
    @NotNull
    @Min(value = 1000, message = "windowSizeMs must be at least 1000ms (1 second)")
    @Column(name = "window_size_ms", nullable = false)
    private Long windowSizeMs;

    /**
     * Token/leak refill rate (tokens per second).
     *
     * Only used by Token Bucket and Leaky Bucket algorithms.
     * Example: refillRate=10.0 → 10 tokens added per second.
     *
     * WHY double? Token accumulation is fractional between refill cycles.
     * e.g., 10 tokens/sec → 0.1 tokens per ms.
     */
    @Column(name = "refill_rate")
    private Double refillRate;

    /**
     * Maximum bucket/queue capacity.
     *
     * For Token Bucket: max tokens before "bucket full" (burst capacity).
     * For Leaky Bucket: max queue depth before overflow (reject).
     *
     * Null for Fixed Window / Sliding Window algorithms (they use maxRequests
     * directly).
     */
    @Column(name = "bucket_size")
    private Integer bucketSize;

    /**
     * Use Redis-backed distributed limiter for this policy?
     *
     * false → in-memory (single-node, loses state on restart, faster)
     * true → Redis (distributed, survives restarts, works across instances)
     *
     * @Builder.Default: Required because @Builder doesn't honor field initializers.
     *                   Without this, the builder would set useRedis=null (not
     *                   false) by default.
     *                   WHY? Lombok's @Builder creates a separate "builder state"
     *                   object — it doesn't
     *                   read your field default. @Builder.Default explicitly wires
     *                   it.
     */
    @Builder.Default
    @Column(name = "use_redis", nullable = false)
    private Boolean useRedis = false;

    /**
     * Is this policy active?
     *
     * Disabled policies are stored but not applied.
     * This allows temporarily suspending a policy without deleting it —
     * think of it as a "feature flag" for rate limit policies.
     */
    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * Free-text description for operators.
     * Not used by rate limiting logic — purely for human documentation.
     */
    @Column(length = 500)
    private String description;

    // ── Convenience Methods ───────────────────────────────────────────────────

    /**
     * Check if a given key matches this policy's pattern.
     *
     * Simple glob-style matching:
     * "*" → matches everything
     * "prefix*"→ matches any key starting with "prefix"
     * "*suffix"→ matches any key ending with "suffix"
     * "exact" → exact match
     *
     * WHY not regex? Regex opens up ReDoS attacks if policy patterns
     * come from untrusted sources. Simple glob is safer.
     *
     * @param key the incoming rate limit key (userId, IP, or path)
     * @return true if this policy applies to the key
     */
    public boolean matches(String key) {
        // NOTE: currently not handling exact regex, ie., "*abc*def*"
        if (key == null || keyPattern == null)
            return false;

        if ("*".equals(keyPattern))
            return true;

        if (keyPattern.endsWith("*")) {
            // Prefix match: "user_*" matches "user_123", "user_abc"
            String prefix = keyPattern.substring(0, keyPattern.length() - 1);
            return key.startsWith(prefix);
        }

        if (keyPattern.startsWith("*")) {
            // Suffix match: "*@gmail.com" matches "alice@gmail.com"
            String suffix = keyPattern.substring(1);
            return key.endsWith(suffix);
        }

        // Exact match
        return keyPattern.equals(key);
    }
}
