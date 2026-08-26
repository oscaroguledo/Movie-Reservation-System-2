package auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import auth.model.User;
import auth.model.UserType;
import auth.repository.RevokedTokenRepository;
import auth.security.InvalidTokenException;
import auth.security.JwtProvider;

class TokenServiceTest {

    private final JwtProvider jwtProvider = new JwtProvider("test-secret-key-at-least-32-bytes-long!!", 30);
    private final RevokedTokenRepository revokedTokenRepository = mock(RevokedTokenRepository.class);
    private final TokenService tokenService = new TokenService(jwtProvider, revokedTokenRepository);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("jane@example.com", "Jane", "Doe", "hashed-password", UserType.REGULAR);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void validatesANonRevokedToken() {
        when(revokedTokenRepository.existsById(anyString())).thenReturn(false);

        String token = tokenService.issueAccessToken(user).token();

        assertThat(tokenService.validate(token).getSubject()).isEqualTo(user.getId().toString());
    }

    @Test
    void rejectsARevokedToken() {
        when(revokedTokenRepository.existsById(anyString())).thenReturn(true);

        String token = tokenService.issueAccessToken(user).token();

        assertThatThrownBy(() -> tokenService.validate(token))
                .isInstanceOf(InvalidTokenException.class);
    }
}
