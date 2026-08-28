package auth.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import auth.IntegrationTestSupport;
import auth.model.UserType;
import auth.repository.RevokedTokenRepository;
import auth.repository.UserRepository;

/**
 * Boots the real Spring context against throwaway Postgres + Kafka
 * containers, publishes each {@link AuthEvent} kind, and awaits
 * {@link AuthEventWorker} actually persisting it — proving the async
 * write pipeline (publish -&gt; consume -&gt; persist), not just that the
 * classes compile against each other.
 */
@SpringBootTest
class AuthEventWorkerIntegrationTest extends IntegrationTestSupport {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private AuthEventPublisher authEventPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    void aUserRegisteredEventIsEventuallyPersisted() {
        UUID userId = UUID.randomUUID();

        authEventPublisher.publish(
                new UserRegistered(userId, "regular", "async@example.com", "Async", "User", "hashed-password"));

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            var found = userRepository.findById(userId);
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("async@example.com");
            assertThat(found.get().getUserType()).isEqualTo(UserType.REGULAR);
        });
    }

    @Test
    void aUserUpdatedEventIsEventuallyReflected() {
        UUID userId = UUID.randomUUID();
        authEventPublisher.publish(
                new UserRegistered(userId, "regular", "before@example.com", "Before", "Update", "hash"));
        await().atMost(AWAIT_TIMEOUT).until(() -> userRepository.existsById(userId));

        authEventPublisher.publish(
                new UserUpdated(userId, "admin", "after@example.com", "After", "Update", "hash"));

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            var found = userRepository.findById(userId);
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("after@example.com");
            assertThat(found.get().getUserType()).isEqualTo(UserType.ADMIN);
        });
    }

    @Test
    void aUserDeletedEventIsEventuallyApplied() {
        UUID userId = UUID.randomUUID();
        authEventPublisher.publish(
                new UserRegistered(userId, "regular", "todelete@example.com", "To", "Delete", "hash"));
        await().atMost(AWAIT_TIMEOUT).until(() -> userRepository.existsById(userId));

        authEventPublisher.publish(new UserDeleted(userId));

        await().atMost(AWAIT_TIMEOUT).until(() -> !userRepository.existsById(userId));
    }

    @Test
    void aTokenRevokedEventIsEventuallyPersisted() {
        String jti = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(Instant.now().plusSeconds(3600), ZoneOffset.UTC);

        authEventPublisher.publish(new TokenRevoked(jti, expiresAt));

        await().atMost(AWAIT_TIMEOUT).until(() -> revokedTokenRepository.existsById(jti));
    }
}
