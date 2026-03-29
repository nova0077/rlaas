local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_size = tonumber(ARGV[2]) -- in ms

local current = redis.call('INCR', key)

-- set expiry only when key is first created
if current == 1 then
    redis.call('PEXPIRE', key, window_size)
end

local allowed = 0
if current <= limit then
    allowed = 1
end

return {allowed, limit-current}