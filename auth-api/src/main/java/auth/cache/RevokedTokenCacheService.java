package auth.cache;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis cache-aside layer for token revocation checks, keyed by jti with
 * a TTL matching the token's own remaining lifetime (no point outliving
 * it — an expired token is rejected on that basis regardless). A cache
 * miss here does not mean "not revoked": it means "check Postgres",
 * which stays the source of truth.
 */
@Component
public class RevokedTokenCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RevokedTokenCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void markRevoked(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(key(jti), Boolean.TRUE, ttl);
    }

    public boolean isKnownRevoked(String jti) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().get(key(jti)));
    }

    private static String key(String jti) {
        return "auth:revoked-token:" + jti;
    }
}
