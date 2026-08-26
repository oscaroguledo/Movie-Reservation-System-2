package auth.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import auth.model.RevokedToken;
import auth.model.User;
import auth.repository.RevokedTokenRepository;
import auth.security.InvalidTokenException;
import auth.security.JwtProvider;
import io.jsonwebtoken.Claims;

/**
 * Wraps {@link JwtProvider} with server-side revocation: a token is
 * rejected once its {@code jti} is recorded in {@code revoked_tokens}
 * (e.g. on logout), even if its signature and expiry are still valid.
 *
 * <p>Currently checks revocation against Postgres directly; a Redis
 * cache-aside layer in front of it lands in a later step, matching the
 * Python reference's "Redis is a cache-aside layer that can always be
 * rebuilt from it" design.
 */
@Service
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RevokedTokenRepository revokedTokenRepository;

    public TokenService(JwtProvider jwtProvider, RevokedTokenRepository revokedTokenRepository) {
        this.jwtProvider = jwtProvider;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public JwtProvider.IssuedToken issueAccessToken(User user) {
        return jwtProvider.issueAccessToken(user);
    }

    public Claims validate(String token) {
        Claims claims = jwtProvider.parseAndValidate(token);
        if (revokedTokenRepository.existsById(claims.getId())) {
            throw new InvalidTokenException("Token has been revoked");
        }
        return claims;
    }

    public void revoke(String jti, Instant expiresAt) {
        revokedTokenRepository.save(new RevokedToken(jti, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));
    }
}
