package movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.OffsetDateTime;
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
import org.springframework.test.context.TestPropertySource;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Drives /admin/* end to end: admin-only gating, and capacity/revenue reflect a real confirmed booking. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "movie.reservation.rate-limit.max-requests=1000")
class AdminReportFlowIntegrationTest extends IntegrationTestSupport {

    private static final String JWT_SECRET = "dev-only-insecure-secret-change-me-32-bytes-min";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void adminReportEndpointsAreAdminOnly() {
        String regularToken = mintToken("regular");
        HttpEntity<Void> asRegular = new HttpEntity<>(authHeaders(regularToken));

        assertThat(rest.exchange("/admin/reservations", HttpMethod.GET, asRegular, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/admin/revenue", HttpMethod.GET, asRegular, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void capacityAndRevenueReflectAConfirmedBooking() {
        String adminToken = mintToken("admin");
        HttpEntity<Void> asAdmin = new HttpEntity<>(authHeaders(adminToken));

        ResponseEntity<Map> movie = rest.exchange(
                "/movies", HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("title", "M " + UUID.randomUUID(), "description", "d", "posterImageUrl", "https://x/p.jpg"),
                        authHeaders(adminToken)),
                Map.class);
        String movieId = (String) movie.getBody().get("id");

        ResponseEntity<Map> showroom = rest.exchange(
                "/showrooms", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "R " + UUID.randomUUID(), "capacity", 10), authHeaders(adminToken)),
                Map.class);
        String showroomId = (String) showroom.getBody().get("id");
        rest.exchange(
                "/showrooms/" + showroomId + "/seats", HttpMethod.POST,
                new HttpEntity<>(Map.of("rows", 1, "seatsPerRow", 1), authHeaders(adminToken)), List.class);

        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        ResponseEntity<Map> screening = rest.exchange(
                "/screenings", HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "movieId", movieId,
                                "showroomId", showroomId,
                                "startTime", start.toString(),
                                "endTime", start.plusHours(2).toString(),
                                "price", "10.00"),
                        authHeaders(adminToken)),
                Map.class);
        String showtimeId = (String) ((Map<?, ?>) screening.getBody().get("showtime")).get("id");

        List<?> seats = await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> rest.getForEntity("/showrooms/" + showroomId + "/seats", List.class).getBody(),
                        s -> !s.isEmpty());
        String seatId = (String) ((Map<?, ?>) seats.get(0)).get("id");

        ResponseEntity<List> hold = rest.postForEntity(
                "/reservations",
                Map.of(
                        "movieId", movieId, "showroomId", showroomId, "showtimeId", showtimeId,
                        "showroomSeatIds", List.of(seatId)),
                List.class);
        String reservationId = (String) ((Map<?, ?>) hold.getBody().get(0)).get("id");
        rest.postForEntity("/reservations/" + reservationId + "/confirm", Map.of("amount", "10.00"), Map.class);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<Map> capacity = rest.exchange(
                    "/admin/screenings/" + movieId + "/" + showroomId + "/" + showtimeId + "/capacity",
                    HttpMethod.GET, asAdmin, Map.class);
            assertThat(capacity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(capacity.getBody().get("totalSeats")).isEqualTo(1);
            assertThat(capacity.getBody().get("booked")).isEqualTo(1);
            assertThat(capacity.getBody().get("available")).isEqualTo(0);
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<Map> revenue = rest.exchange("/admin/revenue", HttpMethod.GET, asAdmin, Map.class);
            assertThat(revenue.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(new java.math.BigDecimal(revenue.getBody().get("gross").toString()))
                    .isGreaterThanOrEqualTo(new java.math.BigDecimal("10.00"));
        });

        ResponseEntity<List> allReservations =
                rest.exchange("/admin/reservations?status=confirmed", HttpMethod.GET, asAdmin, List.class);
        assertThat(allReservations.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static String mintToken(String userType) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("user_type", userType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private static HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
