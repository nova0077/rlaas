local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_size = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

local data = redis.call(
    'HMGET', key,
    'prev_window_count',
    'current_window_start',
    'current_window_count'
)
local prev_window_count = tonumber(data[1])
local current_window_start = tonumber(data[2])
local current_window_count = tonumber(data[3])

if current_window_start == nil or current_window_count == nil then
    current_window_start = now - (now % window_size)
    current_window_count = 0
    prev_window_count = 0
end

local ellapsed_time = now - current_window_start
if ellapsed_time >= window_size then
    local windows_passed = math.floor(ellapsed_time / window_size)
    
    if windows_passed > 1 then
        prev_window_count = 0
    else
        prev_window_count = current_window_count
    end
    current_window_start = now - (now % window_size)
    current_window_count = 0
    ellapsed_time = now - current_window_start
end

local remaining_window_duration = window_size - ellapsed_time
local weighted_count = prev_window_count * (remaining_window_duration/ window_size) + current_window_count

local allowed = 0
local remaining_count = 0
if weighted_count < limit then
    allowed = 1
    current_window_count = current_window_count + 1
    remaining_count = limit - weighted_count - 1
end

redis.call(
        'HMSET', key,
        'prev_window_count',prev_window_count,
        'current_window_start', current_window_start,
        'current_window_count', current_window_count
    )

redis.call('PEXPIRE', key, window_size*2)

return {allowed, remaining_count}