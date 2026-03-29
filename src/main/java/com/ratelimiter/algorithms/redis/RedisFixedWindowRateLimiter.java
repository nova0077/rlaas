package com.ratelimiter.algorithms.redis;

import com.ratelimiter.algorithms.RateLimitResult;
import com.ratelimiter.algorithms.RateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class RedisFixedWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final long windowSizeMs;
    private final int maxRequests;

    // The Lua script executed on Redis
    private final RedisScript<List<Long>> script;

    public RedisFixedWindowRateLimiter(StringRedisTemplate redisTemplate, long windowSizeMs, int maxRequests) {
        this.redisTemplate = redisTemplate;
        this.windowSizeMs = windowSizeMs;
        this.maxRequests = maxRequests;

        // Initialize the Lua script
        try {
            String lua = Files.readString(Path.of("/LuaScripts/fixedWindow.lua"));

            // DefaultRedisScript parses the Lua script and defines the return type
            // (List.class)
            @SuppressWarnings("unchecked")
            Class<List<Long>> listType = (Class<List<Long>>) (Class<?>) List.class;
            this.script = new DefaultRedisScript<>(lua, listType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Redis Lua script for Fixed Window Rate Limiter", e);
        }
    }

    @Override
    public RateLimitResult check(String key) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / windowSizeMs) * windowSizeMs;
        long resetAt = currentWindowStart + windowSizeMs;

        String redisKey = "ratelimit:fw:" + key + ":" + currentWindowStart;

        // Execute the Lua script atomically
        List<?> result = redisTemplate.execute(
                script,
                Collections.singletonList(redisKey), // KEYS[1]
                String.valueOf(maxRequests), // ARGV[1]
                String.valueOf(windowSizeMs) // ARGV[2] (TTL in ms)
        );

        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Invalid Lua script result in RedisFixedWindowRateLimiter");
        }

        boolean allowed = ((Number) result.get(0)).longValue() == 1L;
        int remaining = ((Number) result.get(1)).intValue();

        if (allowed) {
            return RateLimitResult.allowed(maxRequests, remaining, resetAt, getAlgorithmName());
        } else {
            long retryAfter = resetAt - now;
            return RateLimitResult.rejected(maxRequests, resetAt, retryAfter, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() {
        return "Redis Fixed Window";
    }

    @Override
    public void reset(String key) {
        // Find all keys starting with "ratelimit:fw:{key}:*" and delete them
        redisTemplate.keys("ratelimit:fw:" + key + ":*").forEach(redisTemplate::delete);
    }

    @Override
    public String getState(String key) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / windowSizeMs) * windowSizeMs;
        String redisKey = "ratelimit:fw:" + key + ":" + currentWindowStart;

        String value = redisTemplate.opsForValue().get(redisKey);
        int currentCount = value != null ? Integer.parseInt(value) : 0;

        return String.format(
                "----- Redis Fixed Window State -----\n" +
                        "Window Size: %dms\n" +
                        "Max Requests: %d\n" +
                        "Current Window Start: %d\n" +
                        "Current Count: %d\n" +
                        "Remaining: %d\n",
                windowSizeMs, maxRequests, currentWindowStart, currentCount, Math.max(0, maxRequests - currentCount));
    }

    @Override
    public void cleanup() {
        // No-op. Redis handles cleanup automatically via PEXPIRE (TTL).
    }
}
