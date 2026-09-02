package auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import auth.model.User;
import auth.model.UserType;
import auth.repository.UserRepository;

class InitialAdminSeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void doesNothingWhenNotConfigured() {
        InitialAdminSeeder seeder =
                new InitialAdminSeeder(userRepository, passwordEncoder, "", "", "Admin", "User");

        seeder.run(null);

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenOnlyPasswordIsSet() {
        InitialAdminSeeder seeder =
                new InitialAdminSeeder(userRepository, passwordEncoder, "", "Str0ngPass!", "Admin", "User");

        seeder.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenOnlyEmailIsSet() {
        InitialAdminSeeder seeder = new InitialAdminSeeder(
                userRepository, passwordEncoder, "admin@example.com", "", "Admin", "User");

        seeder.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenAnAccountWithThatEmailAlreadyExists() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);
        InitialAdminSeeder seeder = new InitialAdminSeeder(
                userRepository, passwordEncoder, "admin@example.com", "Str0ngPass!", "Admin", "User");

        seeder.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsTheAdminWhenConfiguredAndNoneExistsYet() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Str0ngPass!")).thenReturn("hashed-password");
        InitialAdminSeeder seeder = new InitialAdminSeeder(
                userRepository, passwordEncoder, "admin@example.com", "Str0ngPass!", "Ada", "Lovelace");

        seeder.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getUserType()).isEqualTo(UserType.ADMIN);
        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isEqualTo("Lovelace");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getId()).isNotNull();
    }
}
