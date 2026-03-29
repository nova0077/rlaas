-- KEYS[1] = bucket key
-- ARGV[1] = capacity
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current time in ms


local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- fetch current state
local data = redis.call('HMGET', key, "tokens", "timestamp")
local tokens = tonumber(data[1])
local last_refill_time = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_refill_time = now
end

local elapsed_ms = math.max(0, now-last_refill_time)
local tokens_to_add = (elapsed_ms/ 100.0) * refill_rate

tokens = math.min(capacity, tokens + tokens_to_add)

local allowed = false
if tokens >= 1 then
    tokens = tokens - 1
    allowed = true
end

redis.call(
    'HSET', key,
    "tokens", tokens,
    "timestamp", now
)

-- set expiry for 1 minute
redis.call("PEXPIRE", key, 60000)

return {allowed, tokens}