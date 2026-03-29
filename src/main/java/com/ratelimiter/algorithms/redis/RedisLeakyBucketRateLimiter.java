package com.ratelimiter.algorithms.redis;

import com.ratelimiter.algorithms.RateLimitResult;
import com.ratelimiter.algorithms.RateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class RedisLeakyBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final double leakRate; // requests per second that "leak"
    private final int bucketSize; // maximum queue depth

    private final RedisScript<List<Long>> script;

    public RedisLeakyBucketRateLimiter(StringRedisTemplate redisTemplate, double leakRate, int bucketSize) {
        this.redisTemplate = redisTemplate;
        this.leakRate = leakRate;
        this.bucketSize = bucketSize;

        try {
            String lua = Files.readString(Path.of("/LuaScripts/leakyBucket.lua"));

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
        String redisKey = "ratelimit:lb:" + key;

        List<Long> result = redisTemplate.execute(
                script,
                Arrays.asList(redisKey),
                String.valueOf(now),
                String.valueOf(leakRate),
                String.valueOf(bucketSize));

        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Invalid Lua script result in RedisLeakyBucketRateLimiter");
        }

        boolean allowed = ((Long) result.get(0)) == 1L;
        int remaining = Math.max(0, ((Long) result.get(1)).intValue());
        double currentQueueLevel = (bucketSize - remaining);

        if (allowed) {
            // Reset when the queue is completely empty
            long resetAt = now + (long) ((currentQueueLevel / leakRate) * 1000);
            return RateLimitResult.allowed(bucketSize, remaining, resetAt, getAlgorithmName());
        } else {
            // Need to drop at least 1 unit to allow the next request
            long retryAfter = (long) Math.ceil(1000.0 / leakRate);
            long resetAt = now + (long) Math.ceil((bucketSize / leakRate) * 1000);
            return RateLimitResult.rejected(bucketSize, resetAt, retryAfter, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() {
        return "Redis Leaky Bucket";
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete("ratelimit:lb:" + key);
    }

    @Override
    public String getState(String key) {
        String redisKey = "ratelimit:lb:" + key;
        List<Object> values = redisTemplate.opsForHash().multiGet(redisKey, Arrays.asList("queueLevel", "lastLeaked"));

        String queueStr = values.get(0) != null ? (String) values.get(0) : "empty";

        return String.format(
                "----- Redis Leaky Bucket State -----\n" +
                        "Bucket Size: %d (Max Queue Depth)\n" +
                        "Leak Rate: %.1f req/sec\n" +
                        "Current Queue Level: %s\n",
                bucketSize, leakRate, queueStr);
    }

    @Override
    public void cleanup() {
        // Handled automatically by PEXPIRE in Lua script
    }
}
