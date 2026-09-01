package auth.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import auth.cache.UserCacheService;
import auth.dto.RegisterRequest;
import auth.dto.UpdateUserRequest;
import auth.event.AuthEventPublisher;
import auth.event.UserDeleted;
import auth.event.UserRegistered;
import auth.event.UserUpdated;
import auth.model.User;
import auth.model.UserType;
import auth.repository.UserRepository;
import auth.web.EmailAlreadyRegisteredException;
import auth.web.UserNotFoundException;

/**
 * Register/read/update/delete for {@link User}, through the same
 * cache-aside + Kafka write pipeline as token revocation: Redis is
 * written first for read-your-writes consistency, and {@code
 * AuthEventWorker} updates Postgres asynchronously.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final AuthEventPublisher authEventPublisher;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            UserCacheService userCacheService,
            AuthEventPublisher authEventPublisher,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userCacheService = userCacheService;
        this.authEventPublisher = authEventPublisher;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {
        if (emailInUse(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        // Always REGULAR: self-registration never grants admin.
        User user = new User(
                UUID.randomUUID(),
                request.email(),
                request.firstName(),
                request.lastName(),
                passwordEncoder.encode(request.password()),
                UserType.REGULAR);

        userCacheService.put(user);
        authEventPublisher.publish(new UserRegistered(
                user.getId(),
                user.getUserType().getValue(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPasswordHash()));
        return user;
    }

    public Optional<User> findByEmail(String email) {
        return userCacheService.getByEmail(email).or(() -> userRepository.findByEmail(email));
    }

    public User getById(UUID id) {
        return userCacheService
                .getById(id)
                .or(() -> userRepository.findById(id))
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public User update(UUID id, UpdateUserRequest request) {
        User user = getById(id);

        String firstName = request.firstName() != null ? request.firstName() : user.getFirstName();
        String lastName = request.lastName() != null ? request.lastName() : user.getLastName();
        String passwordHash =
                request.password() != null ? passwordEncoder.encode(request.password()) : user.getPasswordHash();
        user.applyUpdate(firstName, lastName, passwordHash);

        userCacheService.put(user);
        authEventPublisher.publish(new UserUpdated(
                id, user.getUserType().getValue(), user.getEmail(), firstName, lastName, passwordHash));
        return user;
    }

    public void delete(UUID id) {
        User user = getById(id);
        userCacheService.evict(user);
        authEventPublisher.publish(new UserDeleted(id));
    }

    private boolean emailInUse(String email) {
        return userCacheService.getByEmail(email).isPresent() || userRepository.existsByEmail(email);
    }
}
