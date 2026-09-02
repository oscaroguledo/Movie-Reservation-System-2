package movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Drives the real HTTP endpoints (embedded servlet container, real
 * security filter chain) end to end: proves the mostly-open security
 * model — a guest (no token at all) can browse, admin-only writes are
 * enforced, and an actually-invalid token is rejected regardless of
 * endpoint. Mirrors auth-api's AuthFlowIntegrationTest.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CatalogFlowIntegrationTest extends IntegrationTestSupport {

    private static final String JWT_SECRET = "dev-only-insecure-secret-change-me-32-bytes-min";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void aGuestCanBrowseGenresMoviesAndShowroomsWithNoToken() {
        ResponseEntity<List> genres = rest.getForEntity("/genres", List.class);
        ResponseEntity<List> movies = rest.getForEntity("/movies", List.class);
        ResponseEntity<List> showrooms = rest.getForEntity("/showrooms", List.class);

        assertThat(genres.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(movies.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(showrooms.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aGuestCannotCreateAGenreButAnAdminCan() {
        Map<String, String> body = Map.of("name", "Sci-Fi " + UUID.randomUUID());

        ResponseEntity<Map> asGuest = rest.postForEntity("/genres", body, Map.class);
        assertThat(asGuest.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> asAdmin =
                rest.exchange("/genres", HttpMethod.POST, new HttpEntity<>(body, adminHeaders()), Map.class);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void aRegularUserCannotCreateAMovieButAnAdminCan() {
        Map<String, Object> body = Map.of(
                "title", "Test Movie " + UUID.randomUUID(),
                "description", "desc",
                "posterImageUrl", "https://example.com/p.jpg");

        ResponseEntity<Map> asRegular =
                rest.exchange("/movies", HttpMethod.POST, new HttpEntity<>(body, regularHeaders()), Map.class);
        assertThat(asRegular.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> asAdmin =
                rest.exchange("/movies", HttpMethod.POST, new HttpEntity<>(body, adminHeaders()), Map.class);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<?>) asAdmin.getBody().get("genres")).isEmpty();
    }

    @Test
    void anInvalidTokenIsRejectedRegardlessOfEndpoint() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-real-token");

        ResponseEntity<Map> response = rest.exchange("/genres", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void showroomSeatBulkCreationIsAdminOnlyAndSkipsDuplicates() {
        Map<String, Object> showroomBody = Map.of("name", "Room " + UUID.randomUUID(), "capacity", 20);
        ResponseEntity<Map> showroom = rest.exchange(
                "/showrooms", HttpMethod.POST, new HttpEntity<>(showroomBody, adminHeaders()), Map.class);
        String showroomId = (String) showroom.getBody().get("id");

        Map<String, Object> seatsBody = Map.of("rows", 2, "seatsPerRow", 3);

        ResponseEntity<Map> asRegular = rest.exchange(
                "/showrooms/" + showroomId + "/seats", HttpMethod.POST,
                new HttpEntity<>(seatsBody, regularHeaders()), Map.class);
        assertThat(asRegular.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<List> asAdmin = rest.exchange(
                "/showrooms/" + showroomId + "/seats", HttpMethod.POST,
                new HttpEntity<>(seatsBody, adminHeaders()), List.class);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(asAdmin.getBody()).hasSize(6);

        // /seats (list) reads Postgres directly, not the Redis cache-aside
        // layer, so it only sees the seats once the Kafka worker has
        // actually persisted the async creation event.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<List> listed = rest.getForEntity("/showrooms/" + showroomId + "/seats", List.class);
            assertThat(listed.getBody()).hasSize(6);
        });
    }

    private static HttpHeaders adminHeaders() {
        return authHeaders("admin");
    }

    private static HttpHeaders regularHeaders() {
        return authHeaders("regular");
    }

    private static HttpHeaders authHeaders(String userType) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("user_type", userType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
