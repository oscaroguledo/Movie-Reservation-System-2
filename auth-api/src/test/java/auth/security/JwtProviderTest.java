package auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import auth.model.User;
import auth.model.UserType;
import io.jsonwebtoken.Claims;

class JwtProviderTest {

    private final JwtProvider jwtProvider = new JwtProvider("test-secret-key-at-least-32-bytes-long!!", 30);

    @Test
    void issuesAndParsesAValidToken() {
        User user = aUser();

        JwtProvider.IssuedToken issued = jwtProvider.issueAccessToken(user);
        Claims claims = jwtProvider.parseAndValidate(issued.token());

        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email")).isEqualTo("jane@example.com");
        assertThat(claims.get("user_type")).isEqualTo("regular");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void rejectsATamperedToken() {
        String token = jwtProvider.issueAccessToken(aUser()).token();
        // Flip a character in the middle of the signature segment, not
        // the very last character: base64url's final character of a
        // group can carry only padding bits, so mutating it sometimes
        // decodes to the exact same bytes and the tamper is a no-op.
        int i = token.length() / 2;
        char flipped = token.charAt(i) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, i) + flipped + token.substring(i + 1);

        assertThatThrownBy(() -> jwtProvider.parseAndValidate(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        JwtProvider otherProvider = new JwtProvider("a-completely-different-secret-key-32b!!", 30);
        String token = otherProvider.issueAccessToken(aUser()).token();

        assertThatThrownBy(() -> jwtProvider.parseAndValidate(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    private static User aUser() {
        return new User(UUID.randomUUID(), "jane@example.com", "Jane", "Doe", "hashed-password", UserType.REGULAR);
    }
}
