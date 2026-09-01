package movie.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Generic cache-aside layer shared by the simple CRUD entities (Genre,
 * Movie, Showroom, ...), keyed by {@code movie:{prefix}:{id}}. TTL-bound
 * ({@code movie.reservation.entity-cache-ttl-seconds}) — this is a
 * rebuildable-from-Postgres read cache, not a durability boundary like
 * the reservation seat lock is.
 */
@Component
public class EntityCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration ttl;

    public EntityCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${movie.reservation.entity-cache-ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public void put(String prefix, UUID id, Object entity) {
        redisTemplate.opsForValue().set(key(prefix, id), entity, ttl);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String prefix, UUID id, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key(prefix, id));
        return type.isInstance(value) ? Optional.of((T) value) : Optional.empty();
    }

    public void evict(String prefix, UUID id) {
        redisTemplate.delete(key(prefix, id));
    }

    private static String key(String prefix, UUID id) {
        return "movie:" + prefix + ":" + id;
    }
}
