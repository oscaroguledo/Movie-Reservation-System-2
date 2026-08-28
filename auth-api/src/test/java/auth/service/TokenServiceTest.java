package auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import auth.cache.RevokedTokenCacheService;
import auth.event.AuthEventPublisher;
import auth.event.TokenRevoked;
import auth.model.User;
import auth.model.UserType;
import auth.repository.RevokedTokenRepository;
import auth.security.InvalidTokenException;
import auth.security.JwtProvider;

class TokenServiceTest {

    private final JwtProvider jwtProvider = new JwtProvider("test-secret-key-at-least-32-bytes-long!!", 30);
    private final RevokedTokenRepository revokedTokenRepository = mock(RevokedTokenRepository.class);
    private final RevokedTokenCacheService revokedTokenCacheService = mock(RevokedTokenCacheService.class);
    private final AuthEventPublisher authEventPublisher = mock(AuthEventPublisher.class);
    private final TokenService tokenService =
            new TokenService(jwtProvider, revokedTokenRepository, revokedTokenCacheService, authEventPublisher);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(UUID.randomUUID(), "jane@example.com", "Jane", "Doe", "hashed-password", UserType.REGULAR);
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
    void revokeMarksRedisImmediatelyAndPublishesAKafkaEventForPostgres() {
        Instant expiresAt = Instant.now().plusSeconds(60);

        tokenService.revoke("some-jti", expiresAt);

        verify(revokedTokenCacheService).markRevoked("some-jti", expiresAt);
        verify(authEventPublisher).publish(any(TokenRevoked.class));
        // Postgres is no longer written to synchronously — that's the
        // worker's job once it consumes the published event.
        verify(revokedTokenRepository, never()).save(any());
    }
}
