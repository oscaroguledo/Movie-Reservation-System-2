package auth.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import auth.cache.RevokedTokenCacheService;
import auth.event.AuthEventPublisher;
import auth.event.TokenRevoked;
import auth.model.User;
import auth.repository.RevokedTokenRepository;
import auth.security.InvalidTokenException;
import auth.security.JwtProvider;
import io.jsonwebtoken.Claims;

/**
 * Wraps {@link JwtProvider} with server-side revocation: a token is
 * rejected once its {@code jti} is recorded as revoked (e.g. on logout),
 * even if its signature and expiry are still valid. Revocation checks
 * hit Redis first and fall back to Postgres on a cache miss, matching
 * the Python reference's "Redis is a cache-aside layer that can always
 * be rebuilt from it" design.
 *
 * <p>{@link #revoke} writes to Redis immediately (read-your-writes) and
 * publishes a {@link TokenRevoked} event for the Kafka worker to persist
 * to Postgres asynchronously — the full eventually-consistent pipeline,
 * not a synchronous direct write.
 */
@Service
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RevokedTokenRepository revokedTokenRepository;
    private final RevokedTokenCacheService revokedTokenCacheService;
    private final AuthEventPublisher authEventPublisher;

    public TokenService(
            JwtProvider jwtProvider,
            RevokedTokenRepository revokedTokenRepository,
            RevokedTokenCacheService revokedTokenCacheService,
            AuthEventPublisher authEventPublisher) {
        this.jwtProvider = jwtProvider;
        this.revokedTokenRepository = revokedTokenRepository;
        this.revokedTokenCacheService = revokedTokenCacheService;
        this.authEventPublisher = authEventPublisher;
    }

    public JwtProvider.IssuedToken issueAccessToken(User user) {
        return jwtProvider.issueAccessToken(user);
    }

    public Claims validate(String token) {
        Claims claims = jwtProvider.parseAndValidate(token);
        String jti = claims.getId();
        boolean revoked = revokedTokenCacheService.isKnownRevoked(jti) || revokedTokenRepository.existsById(jti);
        if (revoked) {
            throw new InvalidTokenException("Token has been revoked");
        }
        return claims;
    }

    public void revoke(String jti, Instant expiresAt) {
        revokedTokenCacheService.markRevoked(jti, expiresAt);
        authEventPublisher.publish(new TokenRevoked(jti, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));
    }

    /**
     * Deletes {@code revoked_tokens} rows whose own token has already
     * expired — a revoked token past its {@code exp} is rejected on that
     * basis alone regardless, so the row is harmless to keep but would
     * otherwise grow the table forever. Called periodically by {@link
     * RevokedTokenCleanupTask}.
     */
    @Transactional
    public long purgeExpired(OffsetDateTime now) {
        return revokedTokenRepository.deleteByExpiresAtBefore(now);
    }
}
