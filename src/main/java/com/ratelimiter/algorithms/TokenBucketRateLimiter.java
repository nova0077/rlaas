package com.ratelimiter.algorithms;

import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketRateLimiter implements RateLimiter {
    private final double refillRate;
    private final int bucketSize;

    private final ConcurrentHashMap<String, Bucket> buckets;

    private static class Bucket {
        double tokens;
        long lastRefillAt;

        Bucket(double tokens, long lastRefillAt) {
            this.tokens = tokens;
            this.lastRefillAt = lastRefillAt;
        }
    }

    public TokenBucketRateLimiter(double refillRate, int bucketSize) {
        this.refillRate = refillRate;
        this.bucketSize = bucketSize;
        this.buckets = new ConcurrentHashMap<>();
    }

    public void refillTokens(Bucket bucket) {
        long now = System.currentTimeMillis();
        double secondsPassed = (now - bucket.lastRefillAt)/ 1000.0;
        double tokensToAdd = secondsPassed * refillRate;

        bucket.tokens = Math.min(bucketSize, bucket.tokens + tokensToAdd);
        bucket.lastRefillAt = now;
    }

    @Override
    public RateLimitResult check(String key) {
        long now = System.currentTimeMillis();

        Bucket bucket = buckets.computeIfAbsent(
            key,
            k -> new Bucket(bucketSize, now)
        );

        synchronized (bucket) {
            refillTokens(bucket);
            
            if(bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                int remaining = (int) Math.floor(bucket.tokens);
                long resetAt = now + (long) Math.ceil((bucketSize - bucket.tokens)/ refillRate * 1000);
                return RateLimitResult.allowed(bucketSize, remaining, resetAt, getAlgorithmName());
            } else {
                double requiredTokens = 1-bucket.tokens;
                long retryAfter = (long) (requiredTokens/refillRate * 1000);
                long resetAt = now + retryAfter;
                
                return RateLimitResult.rejected(bucketSize, resetAt, retryAfter, getAlgorithmName());
            }

        }
    }

    @Override
    public String getState(String key) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.get(key);

        StringBuilder sb = new StringBuilder();
        sb.append("===== Token Bucket =====\n");
        sb.append(String.format("Bucket Size: %d tokens\n", bucketSize));
        sb.append(String.format("Refill Rate: %.1f tokens/sec\n", refillRate));

        if(bucket != null) {
            // copy is created to avoid mutating shared state during a read-only operation.
            Bucket copy = new Bucket(bucket.tokens, bucket.lastRefillAt);
            refillTokens(copy);

            sb.append(String.format("Current Tokens: 0.2f\n", copy.tokens));
            sb.append(String.format("Remaining: %d\n", (int) Math.floor(copy.tokens)));
            sb.append(String.format("Last Refill: %d ms ago\n", now - bucket.lastRefillAt));
        } else {
            sb.append("No bucket found, would start with full bucket");
        }

        return sb.toString();
    }
    
    @Override
    public String getAlgorithmName(){
        return "Token Bucket";
    }

    @Override
    public void reset(String key) {
        buckets.remove(key);
    }

    @Override
    public void cleanup() {
        long maxAgeMs = 3600000; // 1hr
        long now = System.currentTimeMillis();

        buckets.entrySet().removeIf(entry ->
            now - entry.getValue().lastRefillAt > maxAgeMs
        );
    }
}