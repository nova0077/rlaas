local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_size = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local req_id = ARGV[4]

local window_start = now - window_size

redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

local count = redis.call('ZCARD', key)

local allowed = 0
if count < limit then
    allowed = 1
    redis.call('ZADD', key, now, req_id)
    redis.call('PEXPIRE', key, window_size)
    return {allowed, limit-count-1} -- {allowed?, remaining_count}
else
    return {allowed, 0}
end