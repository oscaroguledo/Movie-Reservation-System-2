package auth.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import auth.IntegrationTestSupport;
import auth.model.User;
import auth.model.UserType;

/**
 * Boots the real Spring context against a throwaway Redis container to
 * verify the cache-aside helpers round-trip correctly — not just that
 * they compile against a mocked {@code RedisTemplate}.
 */
@SpringBootTest
class RedisCacheIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserCacheService userCacheService;

    @Autowired
    private RevokedTokenCacheService revokedTokenCacheService;

    @Test
    void cachesAndEvictsAUserByIdAndEmail() {
        User user = new User("jane@example.com", "Jane", "Doe", "hashed-password", UserType.ADMIN);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        userCacheService.put(user);

        assertThat(userCacheService.getById(user.getId())).map(User::getEmail).contains("jane@example.com");
        assertThat(userCacheService.getByEmail("jane@example.com")).map(User::getId).contains(user.getId());

        userCacheService.evict(user);

        assertThat(userCacheService.getById(user.getId())).isEmpty();
        assertThat(userCacheService.getByEmail("jane@example.com")).isEmpty();
    }

    @Test
    void getByIdMissesCleanlyForAnUnknownUser() {
        Optional<User> found = userCacheService.getById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void marksAndChecksARevokedToken() {
        String jti = UUID.randomUUID().toString();

        assertThat(revokedTokenCacheService.isKnownRevoked(jti)).isFalse();

        revokedTokenCacheService.markRevoked(jti, Instant.now().plusSeconds(60));

        assertThat(revokedTokenCacheService.isKnownRevoked(jti)).isTrue();
    }

    @Test
    void doesNotCacheAnAlreadyExpiredRevocation() {
        String jti = UUID.randomUUID().toString();

        revokedTokenCacheService.markRevoked(jti, Instant.now().minusSeconds(1));

        assertThat(revokedTokenCacheService.isKnownRevoked(jti)).isFalse();
    }
}
