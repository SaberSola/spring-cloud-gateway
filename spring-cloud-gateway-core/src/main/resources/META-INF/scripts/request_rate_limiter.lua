local tokens_key = KEYS[1]                                               --令牌桶剩余令牌数
local timestamp_key = KEYS[2]                                            --令牌桶最后填充令牌时间，单位：秒。
--redis.log(redis.LOG_WARNING, "tokens_key " .. tokens_key)

local rate = tonumber(ARGV[1])                                           --replenishRate 令牌桶的填充速率
local capacity = tonumber(ARGV[2])                                       --burstCapacity 令牌桶的上限制
local now = tonumber(ARGV[3])                                            --得到从 1970-01-01 00:00:00 开始的秒数。
local requested = tonumber(ARGV[4])                                      --请求令牌桶的数量 默认1

local fill_time = capacity/rate                                          --计算令牌填满需要的时间
local ttl = math.floor(fill_time*2)                                      --保证填满令牌筒子的时间充足

--redis.log(redis.LOG_WARNING, "rate " .. ARGV[1])
--redis.log(redis.LOG_WARNING, "capacity " .. ARGV[2])
--redis.log(redis.LOG_WARNING, "now " .. ARGV[3])
--redis.log(redis.LOG_WARNING, "requested " .. ARGV[4])
--redis.log(redis.LOG_WARNING, "filltime " .. fill_time)
--redis.log(redis.LOG_WARNING, "ttl " .. ttl)

local last_tokens = tonumber(redis.call("get", tokens_key))             --调用get方法叫去剩余令牌数
if last_tokens == nil then
  last_tokens = capacity
end
--redis.log(redis.LOG_WARNING, "last_tokens " .. last_tokens)

local last_refreshed = tonumber(redis.call("get", timestamp_key))       --调用get方法令牌桶最后填充令牌时间
if last_refreshed == nil then
  last_refreshed = 0
end
--redis.log(redis.LOG_WARNING, "last_refreshed " .. last_refreshed)

local delta = math.max(0, now-last_refreshed)                           --计算当前时间-最后填充时间的秒数
local filled_tokens = math.min(capacity, last_tokens+(delta*rate))      --开始填充令牌数 但是不能超过最大容量
local allowed = filled_tokens >= requested
local new_tokens = filled_tokens                                        --new_tokens 令牌筒子剩余数
local allowed_num = 0                                                   --allowed_num 获取成功数目
if allowed then                                                         --这里请求成功 令牌桶剩余令牌数(new_tokens) 减消耗令牌数( requested )，并设置获取成功( allowed_num = 1 )
  new_tokens = filled_tokens - requested
  allowed_num = 1
end                                                                     --请求不成功的话 allowed_num = 0

--redis.log(redis.LOG_WARNING, "delta " .. delta)
--redis.log(redis.LOG_WARNING, "filled_tokens " .. filled_tokens)
--redis.log(redis.LOG_WARNING, "allowed_num " .. allowed_num)
--redis.log(redis.LOG_WARNING, "new_tokens " .. new_tokens)

redis.call("setex", tokens_key, ttl, new_tokens)                         --设置令牌通筒子的剩余数目
redis.call("setex", timestamp_key, ttl, now)                             --设置令牌筒子的最后刷新时间

return { allowed_num, new_tokens }
