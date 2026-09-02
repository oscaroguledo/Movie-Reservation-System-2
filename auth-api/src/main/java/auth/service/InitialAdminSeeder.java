package auth.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import auth.model.User;
import auth.model.UserType;
import auth.repository.UserRepository;

/**
 * Creates the initial admin from env-configured credentials on startup,
 * if one doesn't already exist — the Java equivalent of the Python
 * reference's {@code seed_initial_admin}.
 *
 * <p>This is the only way to bootstrap the very first admin: every
 * admin-management surface ({@code POST /auth/register/admin}, and any
 * future promote-to-admin endpoint) itself requires an existing admin via
 * {@code @PreAuthorize}, which is a deadlock on a fresh system with zero
 * users.
 *
 * <p>Writes straight to Postgres via the repository, bypassing the usual
 * Redis-write-first/Kafka pipeline: this runs once before the app is
 * serving traffic, so there's no "immediate read-your-writes" requirement
 * to satisfy, and a subsequent login falls back to Postgres on a cache
 * miss regardless (see {@code UserService.findByEmail}).
 */
@Component
public class InitialAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;

    public InitialAdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${auth.initial-admin.email:}") String email,
            @Value("${auth.initial-admin.password:}") String password,
            @Value("${auth.initial-admin.first-name:Admin}") String firstName,
            @Value("${auth.initial-admin.last-name:User}") String lastName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User admin = new User(
                UUID.randomUUID(), email, firstName, lastName, passwordEncoder.encode(password), UserType.ADMIN);
        userRepository.save(admin);
        log.info("Seeded initial admin user: {}", email);
    }
}
