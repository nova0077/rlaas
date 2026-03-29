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
import java.util.UUID;

public class RedisSlidingWindowLoggerRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final long windowSizeMs;
    private final int maxRequests;

    private final RedisScript<List<Long>> script;

    public RedisSlidingWindowLoggerRateLimiter(StringRedisTemplate redisTemplate, long windowSizeMs, int maxRequests) {
        this.redisTemplate = redisTemplate;
        this.windowSizeMs = windowSizeMs;
        this.maxRequests = maxRequests;

        String lua;
        try {
            lua = Files.readString(Path.of("/LuaScripts/slidingWindowLogger.lua"));

            @SuppressWarnings("unchecked")
            Class<List<Long>> listType = (Class<List<Long>>) (Class<?>) List.class;
            this.script = new DefaultRedisScript<>(lua, listType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Redis Lua script for Leaky Bucket Rate Limiter", e);
        }
    }

    @Override
    public RateLimitResult check(String key) {
        long now = System.currentTimeMillis();
        String redisKey = "ratelimit:swl:" + key;

        String requestId = now + "-" + UUID.randomUUID().toString();

        List<Long> result = redisTemplate.execute(
                script,
                Collections.singletonList(redisKey), // KEYS[1]
                String.valueOf(now), // ARGV[1]
                String.valueOf(windowSizeMs), // ARGV[2]
                String.valueOf(maxRequests), // ARGV[3]
                requestId // ARGV[4]
        );

        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Invalid Lua script result in RedisSlidingWindowRateLimiter");
        }

        boolean allowed = result.get(0) == 1L;
        int remaining = result.get(1).intValue();
        long resetAt = now + windowSizeMs;

        if (allowed) {
            return RateLimitResult.allowed(maxRequests, remaining, resetAt, getAlgorithmName());
        } else {
            long retryAfter = windowSizeMs;
            return RateLimitResult.rejected(maxRequests, resetAt, retryAfter, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() {
        return "Redis Sliding Window Log";
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete("ratelimit:swl:" + key);
    }

    @Override
    public String getState(String key) {
        String redisKey = "ratelimit:swl:" + key;
        Long count = redisTemplate.opsForZSet().zCard(redisKey);
        int currentCount = count != null ? count.intValue() : 0;

        return String.format(
                "----- Redis Sliding Window Log State -----\n" +
                        "Window Size: %dms\n" +
                        "Max Requests: %d\n" +
                        "Current Requests in Window: %d\n" +
                        "Remaining: %d\n",
                windowSizeMs, maxRequests, currentCount, Math.max(0, maxRequests - currentCount));
    }

    @Override
    public void cleanup() {
        // Automatic via Redis PEXPIRE.
    }
}
