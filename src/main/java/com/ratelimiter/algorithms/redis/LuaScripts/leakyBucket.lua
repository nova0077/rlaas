local key = KEYS[1]
local now = tonumber(ARGV[1])
local leak_rate = tonumber(ARGV[2])
local bucket_size = tonumber(ARGV[3])

local data = redis.call('HMGET', key, 'last_leak', 'current_tokens')
local last_leak = tonumber(data[1])
local current_tokens = tonumber(data[2])

if last_leak == nil or current_tokens == nil then
    last_leak = now
    current_tokens = 0
end

local ellapsed_time = (now - last_leak)/1000.0 -- in seconds
current_tokens = math.max(0, current_tokens - (ellapsed_time * leak_rate))

local allowed = 0
if current_tokens < bucket_size then
    allowed = 1
    current_tokens = current_tokens + 1
end

redis.call(
        'HMSET', key,
        'last_leak', now,
        'current_tokens', current_tokens
    )

local drain_time_ms = math.ceil((current_tokens / leak_rate) * 1000)
redis.call('PEXPIRE', key, math.max(drain_time_ms, 1000))

return {allowed, bucket_size - current_tokens}