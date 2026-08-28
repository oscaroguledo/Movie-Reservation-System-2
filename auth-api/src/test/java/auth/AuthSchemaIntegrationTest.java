package auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import auth.model.RevokedToken;
import auth.model.User;
import auth.model.UserType;
import auth.repository.RevokedTokenRepository;
import auth.repository.UserRepository;

/**
 * Boots the real Spring context against a throwaway Postgres container,
 * so the Flyway migration and the JPA entity mappings are verified
 * against each other, not just against compile-time types.
 */
@SpringBootTest
class AuthSchemaIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    void migratesSchemaAndPersistsAUser() {
        User user = new User(UUID.randomUUID(), "jane@example.com", "Jane", "Doe", "hashed-password", UserType.ADMIN);

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<User> found = userRepository.findByEmail("jane@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getUserType()).isEqualTo(UserType.ADMIN);
    }

    @Test
    void migratesSchemaAndPersistsARevokedToken() {
        String jti = UUID.randomUUID().toString();
        RevokedToken token = new RevokedToken(jti, OffsetDateTime.now().plusHours(1));

        revokedTokenRepository.save(token);

        Optional<RevokedToken> found = revokedTokenRepository.findById(jti);
        assertThat(found).isPresent();
        assertThat(found.get().getRevokedAt()).isNotNull();
    }
}
