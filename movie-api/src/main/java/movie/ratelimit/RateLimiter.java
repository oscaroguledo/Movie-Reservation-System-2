package movie.ratelimit;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed sliding-window rate limiter — same design as auth-api's
 * RateLimiter (see that module for the full rationale on why this is
 * hand-rolled against StringRedisTemplate rather than via Bucket4j's
 * separate Redis client stack).
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String key, int limit, Duration window) {
        String redisKey = "movie:ratelimit:" + key;
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        var ops = redisTemplate.opsForZSet();
        ops.removeRangeByScore(redisKey, 0, windowStart);
        Long count = ops.zCard(redisKey);
        if (count != null && count >= limit) {
            return false;
        }
        ops.add(redisKey, UUID.randomUUID().toString(), now);
        redisTemplate.expire(redisKey, window);
        return true;
    }
}
