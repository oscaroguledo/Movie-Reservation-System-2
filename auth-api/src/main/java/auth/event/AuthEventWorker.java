package auth.event;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import auth.model.RevokedToken;
import auth.model.User;
import auth.model.UserType;
import auth.repository.RevokedTokenRepository;
import auth.repository.UserRepository;

/**
 * Consumes {@link AuthEvent}s from Kafka and persists them to Postgres —
 * the async half of the write pipeline (Redis write-first, Kafka
 * publish, worker persists as source of truth). Runs as a component
 * inside auth-api for now; the Python reference runs the equivalent as a
 * separate {@code worker.py} process, which this could be split into
 * later without changing the event contract.
 */
@Component
public class AuthEventWorker {

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public AuthEventWorker(UserRepository userRepository, RevokedTokenRepository revokedTokenRepository) {
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @KafkaListener(topics = AuthEventPublisher.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onEvent(AuthEvent event) {
        switch (event) {
            case UserRegistered e -> userRepository.save(toUser(
                    e.userId(), e.email(), e.firstName(), e.lastName(), e.passwordHash(), e.userType()));
            case UserUpdated e -> userRepository.save(toUser(
                    e.userId(), e.email(), e.firstName(), e.lastName(), e.passwordHash(), e.userType()));
            case UserDeleted e ->
                // Tolerate the row not existing yet: a delete can race
                // ahead of the register event's own persistence under
                // eventual consistency. Silently no-op'ing here trades
                // strict delivery guarantees for simplicity, matching
                // this pass's scope — a production worker would want a
                // retry/dead-letter strategy instead.
                userRepository.findById(e.userId()).ifPresent(userRepository::delete);
            case TokenRevoked e -> revokedTokenRepository.save(new RevokedToken(e.jti(), e.expiresAt()));
        }
    }

    private static User toUser(
            UUID id, String email, String firstName, String lastName, String passwordHash, String userType) {
        return new User(id, email, firstName, lastName, passwordHash, UserType.fromValue(userType));
    }
}
