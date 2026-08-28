package auth.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import auth.cache.RevokedTokenCacheService;
import auth.model.RevokedToken;
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
 * <p>{@link #revoke} still writes straight to Postgres for now; routing
 * that write through Kafka + an async worker lands in a later step.
 */
@Service
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RevokedTokenRepository revokedTokenRepository;
    private final RevokedTokenCacheService revokedTokenCacheService;

    public TokenService(
            JwtProvider jwtProvider,
            RevokedTokenRepository revokedTokenRepository,
            RevokedTokenCacheService revokedTokenCacheService) {
        this.jwtProvider = jwtProvider;
        this.revokedTokenRepository = revokedTokenRepository;
        this.revokedTokenCacheService = revokedTokenCacheService;
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
        revokedTokenRepository.save(new RevokedToken(jti, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));
    }
}
