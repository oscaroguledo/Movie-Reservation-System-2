package auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import auth.model.User;
import auth.model.UserType;
import auth.service.TokenService;

/**
 * Drives the real HTTP endpoints (embedded servlet container, real
 * security filter chain) end to end: register -&gt; login -&gt; access
 * -&gt; logout -&gt; rejected, plus self-or-admin authorization.
 *
 * <p>All these calls share one client IP (the embedded test server's
 * loopback address), so the rate limit is raised well above what this
 * class's own request volume could trip — {@link RateLimitIntegrationTest}
 * covers the limiter itself.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "auth.rate-limit.max-requests=1000")
class AuthFlowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TokenService tokenService;

    @Test
    void registerLoginAccessSelfLogoutThenRejected() {
        Map<String, String> registerBody = Map.of(
                "email", "flow@example.com",
                "firstName", "Flow",
                "lastName", "User",
                "password", "password123");

        ResponseEntity<Map> registerResponse = rest.postForEntity("/auth/register", registerBody, Map.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).doesNotContainKey("passwordHash");
        String userId = (String) registerResponse.getBody().get("id");

        ResponseEntity<Map> duplicate = rest.postForEntity("/auth/register", registerBody, Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Works immediately via Redis read-your-writes, ahead of the
        // Kafka worker necessarily having persisted the row yet.
        ResponseEntity<Map> loginResponse = rest.postForEntity(
                "/auth/login", Map.of("email", "flow@example.com", "password", "password123"), Map.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) loginResponse.getBody().get("accessToken");
        assertThat(token).isNotBlank();

        ResponseEntity<Map> self = rest.exchange(
                "/users/" + userId, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(self.getBody().get("email")).isEqualTo("flow@example.com");

        ResponseEntity<Map> noAuth = rest.exchange("/users/" + userId, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> logout = rest.exchange(
                "/auth/logout", HttpMethod.POST, new HttpEntity<>(authHeaders(token)), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> afterLogout = rest.exchange(
                "/users/" + userId, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aRegularUserCannotAccessAnotherUsersProfileButAnAdminCan() {
        String tokenA = registerAndLogin("userA@example.com");
        ResponseEntity<Map> registerB = rest.postForEntity(
                "/auth/register",
                Map.of(
                        "email", "userB@example.com",
                        "firstName", "B",
                        "lastName", "User",
                        "password", "password123"),
                Map.class);
        String userBId = (String) registerB.getBody().get("id");

        ResponseEntity<Map> forbidden = rest.exchange(
                "/users/" + userBId, HttpMethod.GET, new HttpEntity<>(authHeaders(tokenA)), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Self-registration only ever creates REGULAR users; mint an admin
        // token directly to test the admin path (admin bootstrapping is a
        // separate concern from this HTTP flow).
        String adminToken = mintTokenFor(UserType.ADMIN);
        ResponseEntity<Map> asAdmin = rest.exchange(
                "/users/" + userBId, HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updatingOwnProfileIsReflectedImmediately() {
        Map<String, String> registerBody = Map.of(
                "email", "update@example.com",
                "firstName", "Before",
                "lastName", "Update",
                "password", "password123");
        ResponseEntity<Map> registerResponse = rest.postForEntity("/auth/register", registerBody, Map.class);
        String userId = (String) registerResponse.getBody().get("id");
        String token = loginAs("update@example.com");

        ResponseEntity<Map> updateResponse = rest.exchange(
                "/users/" + userId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("firstName", "After"), authHeaders(token)),
                Map.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("firstName")).isEqualTo("After");
    }

    private String registerAndLogin(String email) {
        rest.postForEntity(
                "/auth/register",
                Map.of("email", email, "firstName", "First", "lastName", "Last", "password", "password123"),
                Map.class);
        return loginAs(email);
    }

    private String loginAs(String email) {
        ResponseEntity<Map> loginResponse =
                rest.postForEntity("/auth/login", Map.of("email", email, "password", "password123"), Map.class);
        return (String) loginResponse.getBody().get("accessToken");
    }

    private String mintTokenFor(UserType userType) {
        User user = new User(
                UUID.randomUUID(), "minted-" + UUID.randomUUID() + "@example.com", "Minted", "User", "hash",
                userType);
        return tokenService.issueAccessToken(user).token();
    }

    private static HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
