package auth.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import auth.model.User;

/**
 * Redis cache-aside layer for {@link User} reads/writes. Writes go to
 * Redis immediately for read-your-writes consistency; Redis is otherwise
 * rebuildable from Postgres at any time — matching the Python reference's
 * "Redis is a cache-aside layer that can always be rebuilt from it".
 */
@Component
public class UserCacheService {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;

    public UserCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(User user) {
        redisTemplate.opsForValue().set(idKey(user.getId()), user, TTL);
        redisTemplate.opsForValue().set(emailKey(user.getEmail()), user, TTL);
    }

    public Optional<User> getById(UUID id) {
        return Optional.ofNullable((User) redisTemplate.opsForValue().get(idKey(id)));
    }

    public Optional<User> getByEmail(String email) {
        return Optional.ofNullable((User) redisTemplate.opsForValue().get(emailKey(email)));
    }

    public void evict(User user) {
        redisTemplate.delete(idKey(user.getId()));
        redisTemplate.delete(emailKey(user.getEmail()));
    }

    private static String idKey(UUID id) {
        return "auth:user:id:" + id;
    }

    private static String emailKey(String email) {
        return "auth:user:email:" + email;
    }
}
