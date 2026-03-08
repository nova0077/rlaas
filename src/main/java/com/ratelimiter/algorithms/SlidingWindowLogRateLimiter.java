package com.ratelimiter.algorithms;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class SlidingWindowLogRateLimiter implements RateLimiter{
    private final long windowSizeMs;
    private final int maxRequests;
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<Long>> requestLogs;

    public SlidingWindowLogRateLimiter(long windowSizeMs, int maxRequests) {
        this.windowSizeMs = windowSizeMs;
        this.maxRequests = maxRequests;
        this.requestLogs = new ConcurrentHashMap<>();
    }

    @Override
    public RateLimitResult check(String key) {
        long now = System.currentTimeMillis();
        long windowStart = (now/ windowSizeMs) * windowSizeMs;
        
        ConcurrentSkipListSet<Long> timestamps = requestLogs.computeIfAbsent(
            key,
            k -> new ConcurrentSkipListSet<>()
        );

        timestamps.headSet(windowStart, false).clear();
        int currentCount = timestamps.size();

        Long oldestInWindow = timestamps.isEmpty() ? null : timestamps.first();
        long resetAt = oldestInWindow != null ? oldestInWindow + windowSizeMs : now + windowSizeMs;

        if (currentCount < maxRequests) {
            timestamps.add(now);
            int remaining = maxRequests - timestamps.size();
            return RateLimitResult.allowed(maxRequests, remaining, resetAt, getAlgorithmName());
        } else {
            long retryAfter = resetAt - now;
            return RateLimitResult.rejected(maxRequests, resetAt, retryAfter, getAlgorithmName());
        }
    }

    @Override
    public String getState(String key) {
        long now = System.currentTimeMillis();
        long windowStart = (now/windowSizeMs) * windowSizeMs;

        ConcurrentSkipListSet<Long> timestamps = requestLogs.get(key);

        StringBuilder sb = new StringBuilder();
        sb.append("===== Sliding Window Logger State =====");
        sb.append(String.format("Window size: %dms\n", windowSizeMs));
        sb.append(String.format("Maximum Requests: %d\n", maxRequests));

        if(timestamps != null) {
            timestamps.headSet(windowStart, false).clear();

            sb.append(String.format("Timestamps in Window: %d\n", timestamps.size()));
            sb.append(String.format("Remaining: %d\n", maxRequests - timestamps.size()));
        } else {
            sb.append("No requests recorded\n");
        }

        return sb.toString();
    }

    @Override
    public String getAlgorithmName(){
        return "Rate Limiter Logger";
    }

    @Override
    public void reset(String key){
        requestLogs.remove(key);
    }

    @Override
    public void cleanup(){
        long now = System.currentTimeMillis();
        long windowStart = now - windowSizeMs;
        for(ConcurrentSkipListSet<Long> timestamps: requestLogs.values()) {
            timestamps.headSet(windowStart, false).clear();
        }

        requestLogs.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
