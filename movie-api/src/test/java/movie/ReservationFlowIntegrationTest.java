package movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
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

/**
 * Drives the real HTTP endpoints end to end: hold -> double-booking
 * rejected -> confirm (wrong amount rejected, right amount accepted) ->
 * cancel-and-refund, plus guest access and non-owner rejection. Mirrors
 * auth-api's AuthFlowIntegrationTest.
 *
 * <p>Rate limit raised well above what this class's own request volume
 * could trip, same reasoning as auth-api's equivalent override —
 * {@link ReservationRateLimitIntegrationTest} covers the limiter itself.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "movie.reservation.rate-limit.max-requests=1000")
class ReservationFlowIntegrationTest extends IntegrationTestSupport {

    private static final String JWT_SECRET = "dev-only-insecure-secret-change-me-32-bytes-min";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void guestCanHoldConfirmWithCorrectAmountThenCancelAndBeRefunded() {
        Screening screening = createScreening();

        ResponseEntity<List> holdResponse = rest.postForEntity(
                "/reservations",
                Map.of(
                        "movieId", screening.movieId,
                        "showroomId", screening.showroomId,
                        "showtimeId", screening.showtimeId,
                        "showroomSeatIds", List.of(screening.seatId)),
                List.class);
        assertThat(holdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> reservation = (Map<?, ?>) holdResponse.getBody().get(0);
        String reservationId = (String) reservation.get("id");
        assertThat(reservation.get("status")).isEqualTo("pending");
        assertThat(reservation.get("userId")).isNull();

        // Wrong amount -> 402, payment recorded FAILED.
        ResponseEntity<Map> wrongConfirm = rest.postForEntity(
                "/reservations/" + reservationId + "/confirm", Map.of("amount", "1.00"), Map.class);
        assertThat(wrongConfirm.getStatusCode().value()).isEqualTo(402);

        // Right amount -> confirmed.
        ResponseEntity<Map> confirm = rest.postForEntity(
                "/reservations/" + reservationId + "/confirm", Map.of("amount", "10.00"), Map.class);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirm.getBody().get("status")).isEqualTo("confirmed");

        // /payments reads Postgres directly, so it only sees each payment
        // once the Kafka worker has caught up with its async event.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<List> payments =
                    rest.getForEntity("/reservations/" + reservationId + "/payments", List.class);
            assertThat(payments.getBody()).hasSize(2); // the failed attempt + the successful one
        });

        ResponseEntity<Map> cancel = rest.exchange(
                "/reservations/" + reservationId + "/cancel", HttpMethod.PATCH, HttpEntity.EMPTY, Map.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancel.getBody().get("status")).isEqualTo("cancelled");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<List> paymentsAfterRefund =
                    rest.getForEntity("/reservations/" + reservationId + "/payments", List.class);
            assertThat(paymentsAfterRefund.getBody()).hasSize(3);
        });
    }

    @Test
    void doubleBookingTheSameSeatIsRejected() {
        Screening screening = createScreening();
        Map<String, Object> holdBody = Map.of(
                "movieId", screening.movieId,
                "showroomId", screening.showroomId,
                "showtimeId", screening.showtimeId,
                "showroomSeatIds", List.of(screening.seatId));

        ResponseEntity<List> first = rest.postForEntity("/reservations", holdBody, List.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = rest.postForEntity("/reservations", holdBody, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aRegularUserCannotAccessAnotherUsersReservationButAnAdminCan() {
        Screening screening = createScreening();
        String ownerToken = mintToken("regular");

        ResponseEntity<List> hold = rest.exchange(
                "/reservations", HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "movieId", screening.movieId,
                                "showroomId", screening.showroomId,
                                "showtimeId", screening.showtimeId,
                                "showroomSeatIds", List.of(screening.seatId)),
                        authHeaders(ownerToken)),
                List.class);
        String reservationId = (String) ((Map<?, ?>) hold.getBody().get(0)).get("id");

        String otherToken = mintToken("regular");
        ResponseEntity<Map> asOther = rest.exchange(
                "/reservations/" + reservationId, HttpMethod.GET, new HttpEntity<>(authHeaders(otherToken)),
                Map.class);
        assertThat(asOther.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String adminToken = mintToken("admin");
        ResponseEntity<Map> asAdmin = rest.exchange(
                "/reservations/" + reservationId, HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)),
                Map.class);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listingMyReservationsRequiresARealIdentityNotAGuest() {
        ResponseEntity<Map> asGuest = rest.getForEntity("/reservations", Map.class);
        assertThat(asGuest.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = mintToken("regular");
        ResponseEntity<List> asRegular =
                rest.exchange("/reservations", HttpMethod.GET, new HttpEntity<>(authHeaders(token)), List.class);
        assertThat(asRegular.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Screening createScreening() {
        String adminToken = mintToken("admin");
        ResponseEntity<Map> movie = rest.exchange(
                "/movies", HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("title", "M " + UUID.randomUUID(), "description", "d", "posterImageUrl", "https://x/p.jpg"),
                        authHeaders(adminToken)),
                Map.class);
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
                                "movieId", movie.getBody().get("id"),
                                "showroomId", showroomId,
                                "startTime", start.toString(),
                                "endTime", start.plusHours(2).toString(),
                                "price", "10.00"),
                        authHeaders(adminToken)),
                Map.class);
        String showtimeId = (String) ((Map<?, ?>) screening.getBody().get("showtime")).get("id");

        // The seat has to actually exist in Postgres before a reservation
        // (which references it) can be created against it.
        List<?> seats = org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(10))
                .until(() -> rest.getForEntity("/showrooms/" + showroomId + "/seats", List.class).getBody(),
                        s -> !s.isEmpty());
        String seatId = (String) ((Map<?, ?>) seats.get(0)).get("id");

        return new Screening((String) movie.getBody().get("id"), showroomId, showtimeId, seatId);
    }

    private record Screening(String movieId, String showroomId, String showtimeId, String seatId) {
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
