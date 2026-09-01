package auth.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

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

    @PersistenceContext
    private EntityManager entityManager;

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

    /** Always REGULAR: self-registration never grants admin. */
    public User register(RegisterRequest request) {
        return create(request, UserType.REGULAR);
    }

    /** Only reachable via an admin-gated endpoint — see {@code AuthController}. */
    public User registerAdmin(RegisterRequest request) {
        return create(request, UserType.ADMIN);
    }

    private User create(RegisterRequest request, UserType userType) {
        if (emailInUse(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        User user = new User(
                UUID.randomUUID(),
                request.email(),
                request.firstName(),
                request.lastName(),
                passwordEncoder.encode(request.password()),
                userType);

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

    /**
     * Admin listing, filtered and paginated directly against Postgres
     * (source of truth) rather than the cache-aside layer — a reporting-
     * style read, not a hot-path single-entity lookup. Built with a
     * dynamic JPQL string rather than Spring Data's page-number-based
     * {@code Pageable}, since {@code offset} here (matching the Python
     * reference's {@code limit}/{@code offset} params) isn't necessarily
     * a multiple of {@code limit}.
     */
    @Transactional(readOnly = true)
    public List<User> list(UserType userType, String firstName, String lastName, int limit, int offset) {
        StringBuilder jpql = new StringBuilder("SELECT u FROM User u WHERE 1=1");
        if (userType != null) {
            jpql.append(" AND u.userType = :userType");
        }
        if (firstName != null) {
            jpql.append(" AND LOWER(u.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))");
        }
        if (lastName != null) {
            jpql.append(" AND LOWER(u.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))");
        }
        jpql.append(" ORDER BY u.createdAt");

        TypedQuery<User> query = entityManager.createQuery(jpql.toString(), User.class);
        if (userType != null) {
            query.setParameter("userType", userType);
        }
        if (firstName != null) {
            query.setParameter("firstName", firstName);
        }
        if (lastName != null) {
            query.setParameter("lastName", lastName);
        }
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    private boolean emailInUse(String email) {
        return userCacheService.getByEmail(email).isPresent() || userRepository.existsByEmail(email);
    }
}
