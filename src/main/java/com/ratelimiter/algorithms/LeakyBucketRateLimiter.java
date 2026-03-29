package com.ratelimiter.algorithms;

import java.util.concurrent.ConcurrentHashMap;

public class LeakyBucketRateLimiter implements RateLimiter{
    private final double leakRate;
    private final int bucketSize;
    private final ConcurrentHashMap<String, Bucket> buckets;

    private static class Bucket {
        double queueLevel;
        long lastLeakedAt;

        Bucket (double queueLevel, long lastLeakedAt) {
            this.queueLevel = queueLevel;
            this.lastLeakedAt = lastLeakedAt;
        }
    }

    public LeakyBucketRateLimiter(double leakRate, int bucketSize){
        this.leakRate = leakRate;
        this.bucketSize = bucketSize;
        this.buckets = new ConcurrentHashMap<>();
    }

    private void leakRequests(Bucket bucket) {
        long now = System.currentTimeMillis();
        double secondsPassed = (now-bucket.lastLeakedAt)/1000.0;
        double reqsToLeak = secondsPassed * leakRate;

        bucket.queueLevel = Math.max(0, bucket.queueLevel- reqsToLeak);
        bucket.lastLeakedAt = now;
    }

    @Override
    public RateLimitResult check(String key) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(
            key,
            k -> new Bucket(0, now)
        );

        synchronized(bucket) {
            leakRequests(bucket);

            if(bucket.queueLevel < bucketSize) {
                bucket.queueLevel += 1.0;
                int remaining = bucketSize - (int) Math.ceil(bucket.queueLevel);
                
                // reset => when queue becomes empty
                long resetAt = now + (long) ((bucket.queueLevel/leakRate) * 1000);
                return RateLimitResult.allowed(bucketSize, remaining, resetAt, getAlgorithmName());
            } else {
                // Queue is full - overflow!
                long retryAfter = (long) Math.ceil(1000.0/leakRate);
                long resetAt = now + (long) Math.ceil(bucketSize/leakRate) * 1000;
                return RateLimitResult.rejected(bucketSize, resetAt, retryAfter, getAlgorithmName());
            }
        }
    }

    @Override
    public String getState(String key) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.get(key);

        StringBuilder sb = new StringBuilder();
        sb.append("===== Leaky Bucket =====");
        sb.append(String.format("Bucket Size: %d\n", bucketSize));
        sb.append(String.format("Leak rate %.1f req/sec\n", leakRate));
        
        if(bucket != null) {
            Bucket copy = new Bucket(bucket.queueLevel, bucket.lastLeakedAt);
            leakRequests(copy);

            sb.append(String.format("Queue Level: %.2f\n", bucket.queueLevel));
            sb.append(String.format("Remaining Space: %d\n", bucketSize - (int) Math.ceil(copy.queueLevel)));
            sb.append(String.format("Estimated Wait: %.0fms\n", copy.queueLevel/ leakRate * 1000));
            sb.append(String.format("Last Leaked: %d ms ago\n", now-bucket.lastLeakedAt));
        } else {
            sb.append("No bucket found (Queue is empty)");
        }
        return sb.toString();
    }

    @Override
    public String getAlgorithmName() {
        return "Leaky Bucket";
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
            now - entry.getValue().lastLeakedAt > maxAgeMs
        );
    }
}