package movie.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import movie.model.ReservationUserType;

class JwtPrincipalResolverTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long!!";

    private final JwtPrincipalResolver resolver = new JwtPrincipalResolver(SECRET);

    @Test
    void resolvesAValidTokenMatchingAuthApisClaimNames() {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId, "admin", SECRET);

        MoviePrincipal principal = resolver.resolve(token);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.type()).isEqualTo(ReservationUserType.ADMIN);
        assertThat(principal.isGuest()).isFalse();
    }

    @Test
    void resolvesARegularUserToken() {
        String token = tokenFor(UUID.randomUUID(), "regular", SECRET);

        assertThat(resolver.resolve(token).type()).isEqualTo(ReservationUserType.REGULAR);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        String token = tokenFor(UUID.randomUUID(), "regular", "a-completely-different-secret-key-32b!!");

        assertThatThrownBy(() -> resolver.resolve(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("user_type", "regular")
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> resolver.resolve(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsATokenWithAnUnrecognizedUserType() {
        String token = tokenFor(UUID.randomUUID(), "not-a-real-type", SECRET);

        assertThatThrownBy(() -> resolver.resolve(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> resolver.resolve("not-a-jwt-at-all")).isInstanceOf(InvalidTokenException.class);
    }

    private static String tokenFor(UUID userId, String userType, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("user_type", userType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
