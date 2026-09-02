package movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves RateLimitFilter is actually wired in and returns 429 once the
 * (here, deliberately tiny) configured limit is exceeded. Mirrors
 * auth-api's RateLimitIntegrationTest, including the same cross-test
 * key-isolation fix (a synthetic X-Forwarded-For IP) since every test
 * class in this suite shares one Redis container and the loopback
 * client IP.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "movie.reservation.rate-limit.max-requests=2",
            "movie.reservation.rate-limit.window-seconds=60"
        })
class ReservationRateLimitIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void rejectsReservationAttemptsPastTheConfiguredLimit() {
        // Nonexistent movie/showroom/showtime ids are fine — the rate
        // limiter runs before the request reaches the controller, and
        // creating a hold doesn't validate they exist anyway (it just
        // acquires a Redis seat lock, which doesn't care). The seat id
        // DOES have to differ per call though, or the second call
        // legitimately 409s on a seat-lock conflict with the first
        // rather than reaching the rate limiter's rejection at all.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", UUID.randomUUID().toString());
        UUID movieId = UUID.randomUUID();
        UUID showroomId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();

        // The first two go through to the controller and succeed — a
        // JSON array. Only the third is expected to be rate-limited.
        ResponseEntity<List> first = rest.postForEntity(
                "/reservations", holdRequest(movieId, showroomId, showtimeId, headers), List.class);
        ResponseEntity<List> second = rest.postForEntity(
                "/reservations", holdRequest(movieId, showroomId, showtimeId, headers), List.class);
        ResponseEntity<Map> third = rest.postForEntity(
                "/reservations", holdRequest(movieId, showroomId, showtimeId, headers), Map.class);

        assertThat(first.getStatusCode().value()).isNotEqualTo(429);
        assertThat(second.getStatusCode().value()).isNotEqualTo(429);
        assertThat(third.getStatusCode().value()).isEqualTo(429);
    }

    private static HttpEntity<Map<String, Object>> holdRequest(
            UUID movieId, UUID showroomId, UUID showtimeId, HttpHeaders headers) {
        Map<String, Object> body = Map.of(
                "movieId", movieId.toString(),
                "showroomId", showroomId.toString(),
                "showtimeId", showtimeId.toString(),
                "showroomSeatIds", List.of(UUID.randomUUID().toString()));
        return new HttpEntity<>(body, headers);
    }
}
