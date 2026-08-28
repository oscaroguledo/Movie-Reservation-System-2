package auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import auth.cache.RevokedTokenCacheService;
import auth.model.User;
import auth.model.UserType;
import auth.repository.RevokedTokenRepository;
import auth.security.InvalidTokenException;
import auth.security.JwtProvider;

class TokenServiceTest {

    private final JwtProvider jwtProvider = new JwtProvider("test-secret-key-at-least-32-bytes-long!!", 30);
    private final RevokedTokenRepository revokedTokenRepository = mock(RevokedTokenRepository.class);
    private final RevokedTokenCacheService revokedTokenCacheService = mock(RevokedTokenCacheService.class);
    private final TokenService tokenService =
            new TokenService(jwtProvider, revokedTokenRepository, revokedTokenCacheService);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("jane@example.com", "Jane", "Doe", "hashed-password", UserType.REGULAR);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void validatesANonRevokedToken() {
        when(revokedTokenCacheService.isKnownRevoked(anyString())).thenReturn(false);
        when(revokedTokenRepository.existsById(anyString())).thenReturn(false);

        String token = tokenService.issueAccessToken(user).token();

        assertThat(tokenService.validate(token).getSubject()).isEqualTo(user.getId().toString());
    }

    @Test
    void rejectsATokenRevokedAccordingToRedis() {
        when(revokedTokenCacheService.isKnownRevoked(anyString())).thenReturn(true);

        String token = tokenService.issueAccessToken(user).token();

        assertThatThrownBy(() -> tokenService.validate(token))
                .isInstanceOf(InvalidTokenException.class);
        // Redis said revoked — no need to fall back to Postgres.
        verify(revokedTokenRepository, never()).existsById(anyString());
    }

    @Test
    void fallsBackToPostgresOnARedisCacheMissAndRejectsIfRevokedThere() {
        when(revokedTokenCacheService.isKnownRevoked(anyString())).thenReturn(false);
        when(revokedTokenRepository.existsById(anyString())).thenReturn(true);

        String token = tokenService.issueAccessToken(user).token();

        assertThatThrownBy(() -> tokenService.validate(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void revokeWritesToBothRedisAndPostgres() {
        tokenService.revoke("some-jti", java.time.Instant.now().plusSeconds(60));

        verify(revokedTokenCacheService).markRevoked(anyString(), org.mockito.ArgumentMatchers.any());
        verify(revokedTokenRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
