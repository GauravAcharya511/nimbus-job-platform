package com.gauravacharya.nimbus.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Per-user token bucket backed by Redis.
 *
 * The bucket update runs as a Lua script so the read-modify-write is atomic on the
 * Redis server. If Redis is unreachable the limiter fails open: throttling is a
 * protection, not a correctness guarantee, and losing it should not take the API down.
 */
@Service
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;
    private final boolean enabled;
    private final int capacity;
    private final int refillPerSecond;

    public RateLimiter(StringRedisTemplate redis,
                       @Value("${nimbus.ratelimit.enabled:true}") boolean enabled,
                       @Value("${nimbus.ratelimit.capacity:100}") int capacity,
                       @Value("${nimbus.ratelimit.refill-per-second:20}") int refillPerSecond) {
        this.redis = redis;
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;

        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        this.script.setResultType(Long.class);
    }

    public boolean tryAcquire(String identity) {
        if (!enabled) return true;
        try {
            Long allowed = redis.execute(script,
                    List.of("ratelimit:" + identity),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond),
                    String.valueOf(System.currentTimeMillis()));
            return allowed != null && allowed == 1L;
        } catch (Exception e) {
            return true; // fail open
        }
    }

    public int capacity() { return capacity; }
}
