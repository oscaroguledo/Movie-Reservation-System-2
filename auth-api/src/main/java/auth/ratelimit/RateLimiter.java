package auth.ratelimit;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed sliding-window rate limiter (the sorted-set "sliding
 * window log" algorithm): each call is a timestamped member in a ZSET,
 * entries older than the window are trimmed before counting. Implemented
 * directly against Spring Data Redis rather than pulling in Bucket4j's
 * separate Lettuce-based Redis integration module, to avoid a second
 * Redis client stack next to the one already configured in
 * {@code RedisConfig}.
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** @return true if this call is allowed under {@code limit} requests per {@code window}. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        String redisKey = "ratelimit:" + key;
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        var ops = redisTemplate.opsForZSet();
        ops.removeRangeByScore(redisKey, 0, windowStart);
        Long count = ops.zCard(redisKey);
        if (count != null && count >= limit) {
            return false;
        }
        // A UUID member (not just the timestamp) avoids collapsing two
        // calls that land in the same millisecond into one ZSET entry.
        ops.add(redisKey, UUID.randomUUID().toString(), now);
        redisTemplate.expire(redisKey, window);
        return true;
    }
}
