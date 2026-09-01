package auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

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

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserCacheService userCacheService = mock(UserCacheService.class);
    private final AuthEventPublisher authEventPublisher = mock(AuthEventPublisher.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService userService =
            new UserService(userRepository, userCacheService, authEventPublisher, passwordEncoder);

    @Test
    void registerCachesInRedisAndPublishesAnEventInsteadOfWritingPostgresDirectly() {
        when(userCacheService.getByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        User user = userService.register(new RegisterRequest("jane@example.com", "Jane", "Doe", "password123"));

        assertThat(user.getUserType()).isEqualTo(UserType.REGULAR);
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        verify(userCacheService).put(user);
        verify(authEventPublisher).publish(any(UserRegistered.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerAdminCreatesAnAdminUser() {
        when(userCacheService.getByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        User admin = userService.registerAdmin(new RegisterRequest("root@example.com", "Root", "Admin", "hunter22"));

        assertThat(admin.getUserType()).isEqualTo(UserType.ADMIN);
        verify(userCacheService).put(admin);
        verify(authEventPublisher).publish(any(UserRegistered.class));
    }

    @Test
    void registerRejectsAnEmailAlreadyCachedInRedis() {
        when(userCacheService.getByEmail("jane@example.com"))
                .thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() ->
                        userService.register(new RegisterRequest("jane@example.com", "Jane", "Doe", "password123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void registerRejectsAnEmailAlreadyPersistedInPostgres() {
        when(userCacheService.getByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                        userService.register(new RegisterRequest("jane@example.com", "Jane", "Doe", "password123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void getByIdFallsBackToPostgresOnACacheMiss() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "jane@example.com", "Jane", "Doe", "hash", UserType.REGULAR);
        when(userCacheService.getById(id)).thenReturn(Optional.empty());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(userService.getById(id)).isEqualTo(user);
    }

    @Test
    void getByIdThrowsWhenNotFoundAnywhere() {
        UUID id = UUID.randomUUID();
        when(userCacheService.getById(id)).thenReturn(Optional.empty());
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateAppliesOnlyProvidedFieldsAndPublishesAnEvent() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "jane@example.com", "Jane", "Doe", "old-hash", UserType.REGULAR);
        when(userCacheService.getById(id)).thenReturn(Optional.of(user));

        User updated = userService.update(id, new UpdateUserRequest("Janet", null, null));

        assertThat(updated.getFirstName()).isEqualTo("Janet");
        assertThat(updated.getLastName()).isEqualTo("Doe");
        assertThat(updated.getPasswordHash()).isEqualTo("old-hash");
        verify(userCacheService).put(updated);
        verify(authEventPublisher).publish(any(UserUpdated.class));
    }

    @Test
    void deleteEvictsFromCacheAndPublishesAnEvent() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "jane@example.com", "Jane", "Doe", "hash", UserType.REGULAR);
        when(userCacheService.getById(id)).thenReturn(Optional.of(user));

        userService.delete(id);

        verify(userCacheService).evict(user);
        verify(authEventPublisher).publish(any(UserDeleted.class));
    }
}
