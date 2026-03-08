package com.ratelimiter.algorithms;

import java.util.concurrent.ConcurrentHashMap;

public class SlidingWindowCounterRateLimiter implements RateLimiter{
    private final long windowSizeMs;
    private final int maxRequests;
    private final ConcurrentHashMap<String, WindowData> windows;

    private static class WindowData {
        long currentWindowStart;
        int currentCount;
        long prevWindowStart;
        int prevCount;
        
        WindowData(long currentWindowStart){
            this.currentWindowStart = currentWindowStart;
            this.currentCount = 0;
            this.prevWindowStart = currentWindowStart - 1;
            this.prevCount = 0;
        }
    }

    public SlidingWindowCounterRateLimiter(long windowSizeMs, int maxRequests){
        this.windowSizeMs = windowSizeMs;
        this.maxRequests = maxRequests;
        this.windows = new ConcurrentHashMap<>();
    }

    private double calculateWeightedCount(int currentCount, int prevCount, double progressInWindow) {
        // progressInWindow => current window progress (ex 25%)
        // 1 - progressInWindow => picked from prevWindow (ie, 75%)
        return currentCount + (prevCount * (1.0 - progressInWindow));
    }

    @Override
    public RateLimitResult check(String key) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now/ windowSizeMs)* windowSizeMs;
        long prevWindowStart = currentWindowStart - windowSizeMs;

        double progressInWindow = (double) (now - currentWindowStart)/ windowSizeMs;
        WindowData windowData = windows.compute(key, (k, existingData) -> {
            if(existingData == null) {
                return new WindowData(currentWindowStart);
            }
            return existingData;
        });

        synchronized (windowData) {
            if (windowData.currentWindowStart != currentWindowStart) {
                if(windowData.currentWindowStart == prevWindowStart) {
                    // current window becomes previous
                   windowData.prevWindowStart = windowData.currentWindowStart;
                   windowData.prevCount = windowData.currentCount; 
                } else {
                    // we skipped a window, prev count is 0
                    windowData.prevWindowStart = currentWindowStart;
                    windowData.currentCount = 0;
                }
                windowData.currentWindowStart = currentWindowStart;
                windowData.currentCount = 0;
            }

            double weightedCount = calculateWeightedCount(
                windowData.currentCount,
                windowData.prevCount,
                progressInWindow
            );

            long resetAt = currentWindowStart + windowSizeMs;

            if (weightedCount < maxRequests) {
                windowData.currentCount++;
                int remaining = maxRequests - (int) weightedCount - 1;
                return RateLimitResult.allowed(maxRequests, remaining, resetAt, getAlgorithmName());
            } else {
                double requiredProgress = (windowData.currentCount + windowData.prevCount - maxRequests)/ ((double) windowData.prevCount);
                int retryAfter = (int) ((requiredProgress * windowSizeMs) - (now-currentWindowStart));
                return RateLimitResult.rejected(maxRequests, resetAt, retryAfter, getAlgorithmName());                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      
            }
        }
    }

    @Override
    public String getAlgorithmName() {
        return "Sliding Window Counter";
    }

    @Override
    public String getState(String key){
        long now = System.currentTimeMillis();
        long currentWindowStart = (now/windowSizeMs) * windowSizeMs;
        double progressInWindow = (double) (now - currentWindowStart)/ windowSizeMs;

        WindowData windowData = windows.get(key);

        StringBuilder sb = new StringBuilder();
        sb.append("===== Sliding Window Counter State =====\n");
        sb.append(String.format("Window Size: %dms\n", windowSizeMs));
        sb.append(String.format("Max Requests: %d\n", maxRequests));
        sb.append(String.format("Progress in Window: %.1f%%\n", progressInWindow));

        if(windowData != null) {
            double weightedCount = calculateWeightedCount(
                windowData.currentCount,
                windowData.prevCount,
                progressInWindow
            );
            int remaining = Math.max(0, maxRequests - (int) weightedCount);
            sb.append(String.format("Current Count: %d\n", windowData.currentCount));
            sb.append(String.format("Previous window count: %d\n", windowData.prevCount));
            sb.append(String.format("Weighted Count: %.2f\n", weightedCount));
            sb.append(String.format("Remaining: %d\n", remaining));
        } else {
            sb.append("No Requests recorded\n");
        }
        
        return sb.toString();
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }

    @Override 
    public void cleanup() {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now/windowSizeMs) * windowSizeMs;
        long prevWindowStart = currentWindowStart - windowSizeMs;

        windows.entrySet().removeIf(entry -> 
            entry.getValue().currentWindowStart < prevWindowStart
        );
    }
}
