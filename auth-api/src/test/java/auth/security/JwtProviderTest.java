package auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

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
        User user = new User("jane@example.com", "Jane", "Doe", "hashed-password", UserType.REGULAR);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
