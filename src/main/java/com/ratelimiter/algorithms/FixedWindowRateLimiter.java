package com.ratelimiter.algorithms;

import java.util.concurrent.ConcurrentHashMap;
public class FixedWindowRateLimiter implements RateLimiter{
    private final long windowSizeMs;
    private final int maxRequests;

    // Store: key -> Window data
    // Using ConcurrentHashMap for thread safety
    private final ConcurrentHashMap<String, Window> windows;

    // Internal Class to store window data
    private static class Window {
        long windowStart;
        int count;

        Window(long windowStart) {
            this.windowStart = windowStart;
            this.count = 0;
        }
    }

    /**
     * Create a Fixed Window Rate Limiter
     * @param windowSizeMs size of each window in milliseconds
     * @param maxRequests Maximum requests allowed per windown
     */
    public FixedWindowRateLimiter(long windowSizeMs, int maxRequests) {
        this.windowSizeMs = windowSizeMs;
        this.maxRequests = maxRequests;
        this.windows = new ConcurrentHashMap<>();
    }

    @Override
    public RateLimitResult check(String key) {
        long now = System.currentTimeMillis();

        // 5:09:15 => 5:09:00 (floor to minute considering window size is 1 minute)
        long currentWindowStart = (now/ windowSizeMs) * windowSizeMs;

        // Get or creates a window for this key
        Window window = windows.compute(key, (k, existingWindow) -> {
            if(existingWindow == null || existingWindow.windowStart != currentWindowStart) {
                return new Window(currentWindowStart);
            }
            return existingWindow;
        });

        long resetAt = currentWindowStart + windowSizeMs;

        // Synchronize to ensure atomic check and increment
        synchronized(window){
            if(window.count < maxRequests) {
                window.count++;
                int remaining = maxRequests - window.count;
                return RateLimitResult.allowed(maxRequests, remaining, resetAt, getAlgorithmName());
            } else {
                long retryAfter = resetAt - now;
                return RateLimitResult.rejected(maxRequests, resetAt, retryAfter, getAlgorithmName());
            }
        }
    }

    @Override
    public String getAlgorithmName(){
        return "Fixed Window";
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }

    @Override
    public String getState(String key) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now/windowSizeMs)* windowSizeMs;

        Window window = windows.get(key);

        StringBuilder sb = new StringBuilder();
        sb.append("===== Fixed Window State =====\n");
        sb.append(String.format ("Window Size: %dms\n", windowSizeMs));
        sb.append(String.format("Max Requests: %d\n", maxRequests));
        sb.append(String.format("Current Window Start: %d\n", currentWindowStart));

        if(window != null && window.windowStart == currentWindowStart) {
            sb.append(String.format("Current Count: %d\n",window.count));
            sb.append(String.format("Remaining: %d\n", maxRequests-window.count));
        } else {
            sb.append("No Requests in current window\n");
        }

        return sb.toString();
    }

    @Override
    public void cleanup(){
        long now = System.currentTimeMillis();
        long currentWindowStart = (now/windowSizeMs) * windowSizeMs;

        windows.entrySet().removeIf(entry -> 
            entry.getValue().windowStart < currentWindowStart
        );
    }
}
