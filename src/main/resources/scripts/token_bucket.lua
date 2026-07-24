-- Token bucket rate limiter.
--
-- Executed atomically by Redis, so the read-modify-write below cannot interleave
-- with a concurrent request. Doing this in application code would race: two callers
-- could both observe the last token and both be allowed through.
--
-- KEYS[1]  bucket key
-- ARGV[1]  capacity (max tokens)
-- ARGV[2]  refill rate (tokens per second)
-- ARGV[3]  current time in milliseconds
-- returns  1 if the request is allowed, 0 if it is throttled

local key      = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill   = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'updated_at')
local tokens = tonumber(bucket[1])
local updated = tonumber(bucket[2])

if tokens == nil then
  tokens = capacity
  updated = now
end

-- Refill proportionally to elapsed time, capped at capacity.
local elapsed = math.max(0, now - updated) / 1000
tokens = math.min(capacity, tokens + (elapsed * refill))

local allowed = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'updated_at', now)
-- Expire idle buckets so keys do not accumulate for inactive users.
redis.call('PEXPIRE', key, math.ceil((capacity / refill) * 2000))

return allowed
