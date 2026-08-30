package auth.ratelimit;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import auth.IntegrationTestSupport;

/**
 * Proves {@link RateLimitFilter} is actually wired into the security
 * filter chain and returns 429 once the (here, deliberately tiny)
 * configured limit is exceeded.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"auth.rate-limit.max-requests=2", "auth.rate-limit.window-seconds=60"})
class RateLimitIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void rejectsLoginAttemptsPastTheConfiguredLimit() {
        Map<String, String> body = Map.of("email", "nobody@example.com", "password", "wrong-password");
        // The rate limit key is IP + path; other test classes' real
        // requests share the loopback address against the same shared
        // Redis container, so a distinct synthetic IP keeps this test's
        // counter isolated regardless of run order.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", UUID.randomUUID().toString());
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> first = rest.postForEntity("/auth/login", request, Map.class);
        ResponseEntity<Map> second = rest.postForEntity("/auth/login", request, Map.class);
        ResponseEntity<Map> third = rest.postForEntity("/auth/login", request, Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(third.getStatusCode().value()).isEqualTo(429);
    }
}
