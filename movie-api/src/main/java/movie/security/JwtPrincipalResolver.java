package movie.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import movie.model.ReservationUserType;

/**
 * Validates a JWT auth-api issued (same shared secret — see
 * {@code movie.jwt.secret}) and resolves it to a {@link MoviePrincipal}.
 * Unlike auth-api's TokenService, this never checks revocation:
 * movie-api has no access to auth-api's {@code revoked_tokens} table.
 * That's a real, acknowledged limitation in the Python reference itself
 * (its own comment says so) — replicated as-is, not "fixed".
 */
@Component
public class JwtPrincipalResolver {

    private final SecretKey key;

    public JwtPrincipalResolver(@Value("${movie.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public MoviePrincipal resolve(String token) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }

        try {
            UUID userId = UUID.fromString(claims.getSubject());
            ReservationUserType type = ReservationUserType.fromValue((String) claims.get("user_type"));
            return new MoviePrincipal(userId, type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Invalid token claims", e);
        }
    }
}
