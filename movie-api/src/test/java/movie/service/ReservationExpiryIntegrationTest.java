package movie.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import movie.IntegrationTestSupport;
import movie.model.ReservationStatus;
import movie.model.ReservationUserType;
import movie.security.MoviePrincipal;

/**
 * Proves lazy expiry: a PENDING hold whose TTL has passed settles to
 * EXPIRED (and releases its seat lock) the next time it's read, without
 * any background sweep. Uses a 1-second hold TTL so the test doesn't
 * need to wait long.
 */
@SpringBootTest
@TestPropertySource(properties = "movie.reservation.hold-ttl-seconds=1")
class ReservationExpiryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private MovieService movieService;

    @Autowired
    private ShowroomService showroomService;

    @Autowired
    private ScreeningService screeningService;

    @Test
    void aStalePendingHoldExpiresOnReadAndReleasesTheSeat() throws InterruptedException {
        UUID movieId = movieService.create("Movie " + UUID.randomUUID(), "d", "https://x/p.jpg", null, null, null)
                .getId();
        UUID showroomId = showroomService.create("Room " + UUID.randomUUID(), 10).getId();
        var seat = showroomService.bulkCreateSeats(showroomId, 1, 1).get(0);
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        var screening = screeningService.schedule(movieId, showroomId, start, start.plusHours(2), new BigDecimal("10.00"));

        MoviePrincipal guest = MoviePrincipal.GUEST;
        var reservations = reservationService.createHold(
                guest, movieId, showroomId, screening.showtime().getId(), List.of(seat.getId()));
        UUID reservationId = reservations.get(0).getId();

        Thread.sleep(1200);

        var expired = reservationService.get(reservationId);
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        // The seat is free again — a new hold on the same seat succeeds.
        var secondHold = reservationService.createHold(
                new MoviePrincipal(null, ReservationUserType.GUEST), movieId, showroomId,
                screening.showtime().getId(), List.of(seat.getId()));
        assertThat(secondHold.get(0).getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
}
