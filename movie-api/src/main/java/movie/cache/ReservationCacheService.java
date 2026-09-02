package movie.cache;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import movie.model.Reservation;

/**
 * Cache-aside for {@link Reservation}, with NO TTL on the item itself
 * (unlike {@link EntityCacheService}) — a reservation isn't in Postgres
 * yet when it's created, so evicting it from Redis on a timer would
 * lose it entirely until the Kafka worker catches up. The seat lock
 * ({@link SeatLockService}) is what actually expires a stale hold; this
 * is just where the reservation's own data lives until then.
 */
@Component
public class ReservationCacheService {

    private static final String ITEM_PREFIX = "movie:reservation:";
    private static final String USER_INDEX_PREFIX = "movie:reservations-by-user:";

    private final RedisTemplate<String, Object> redisTemplate;

    public ReservationCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(Reservation reservation) {
        redisTemplate.opsForValue().set(itemKey(reservation.getId()), reservation);
        if (reservation.getUserId() != null) {
            redisTemplate.opsForSet().add(userIndexKey(reservation.getUserId()), reservation.getId().toString());
        }
    }

    public Optional<Reservation> get(UUID id) {
        Object value = redisTemplate.opsForValue().get(itemKey(id));
        return value instanceof Reservation reservation ? Optional.of(reservation) : Optional.empty();
    }

    public List<UUID> listIdsForUser(UUID userId) {
        Set<Object> members = redisTemplate.opsForSet().members(userIndexKey(userId));
        return members == null
                ? List.of()
                : members.stream().map(m -> UUID.fromString(m.toString())).toList();
    }

    private static String itemKey(UUID id) {
        return ITEM_PREFIX + id;
    }

    private static String userIndexKey(UUID userId) {
        return USER_INDEX_PREFIX + userId;
    }
}
