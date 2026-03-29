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

public class RedisTokenBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final double refillRate; // tokens per second
    private final int bucketSize;

    private final RedisScript<List<Long>> script;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate, double refillRate, int bucketSize) {
        this.redisTemplate = redisTemplate;
        this.refillRate = refillRate;
        this.bucketSize = bucketSize;

        String lua;
        try {
            lua = Files.readString(Path.of("/LuaScripts/tokenBucket.lua"));

            @SuppressWarnings("unchecked")
            Class<List<Long>> longType = (Class<List<Long>>) (Class<?>) List.class;
            this.script = new DefaultRedisScript<>(lua, longType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Redis Lua script for Token Bucket Rate Limiter", e);
        }
    }

    @Override
    public RateLimitResult check(String key) {
        long now = System.currentTimeMillis();
        String redisKey = "ratelimit:tb:" + key;

        List<Long> result = redisTemplate.execute(
                script,
                Arrays.asList(redisKey), // KEYS[1]
                String.valueOf(now), // ARGV[1]
                String.valueOf(bucketSize), // ARGV[2]
                String.valueOf(refillRate) // ARGV[3]
        );

        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Invalid Lua script result in RedisTokenBucketRateLimiter");
        }

        boolean allowed = ((Long) result.get(0)) == 1L;
        int remaining = ((Long) result.get(1)).intValue();
        double currentTokens = (bucketSize - remaining);

        if (allowed) {
            // How long until bucket is FULL
            long resetAt = now + (long) Math.ceil((bucketSize - currentTokens) / refillRate * 1000);
            return RateLimitResult.allowed(bucketSize, remaining, resetAt, getAlgorithmName());
        } else {
            // How long until we get exactly 1 token so we can retry
            double requiredTokens = 1.0 - currentTokens;
            long retryAfter = (long) (requiredTokens / refillRate * 1000);
            long resetAt = now + retryAfter;
            return RateLimitResult.rejected(bucketSize, resetAt, retryAfter, getAlgorithmName());
        }
    }

    @Override
    public String getAlgorithmName() {
        return "Redis Token Bucket";
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete("ratelimit:tb:" + key);
    }

    @Override
    public String getState(String key) {
        String redisKey = "ratelimit:tb:" + key;
        List<Object> values = redisTemplate.opsForHash().multiGet(redisKey, Arrays.asList("tokens", "lastRefill"));

        String tokensStr = values.get(0) != null ? (String) values.get(0) : "empty";

        return String.format(
                "----- Redis Token Bucket State -----\n" +
                        "Bucket Size: %d tokens\n" +
                        "Refill Rate: %.1f tokens/sec\n" +
                        "Current Tokens (Stored): %s\n",
                bucketSize, refillRate, tokensStr);
    }

    @Override
    public void cleanup() {
        // Handled automatically by PEXPIRE in Lua script
    }
}
