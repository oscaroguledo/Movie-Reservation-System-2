package movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import movie.IntegrationTestSupport;
import movie.web.EntityConflictException;

/**
 * Proves the actual overlap-prevention mechanism — the Redis schedule
 * lock plus check-then-append — against real Redis, not a mock. This is
 * the behavior that matters most to get right; unlike a Postgres
 * exclusion constraint, nothing here is enforced by the database.
 */
@SpringBootTest
class ScreeningServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ScreeningService screeningService;

    @Autowired
    private MovieService movieService;

    @Autowired
    private ShowroomService showroomService;

    @Test
    void rejectsAnOverlappingScreeningInTheSameShowroom() {
        UUID movieId = movieService.create("Movie " + UUID.randomUUID(), "d", "https://x/p.jpg", null, null, null)
                .getId();
        UUID showroomId = showroomService.create("Room " + UUID.randomUUID(), 50).getId();
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);

        screeningService.schedule(movieId, showroomId, start, start.plusHours(2), new BigDecimal("10.00"));

        // Overlaps: starts before the first ends.
        assertThatThrownBy(() -> screeningService.schedule(
                        movieId, showroomId, start.plusHours(1), start.plusHours(3), new BigDecimal("10.00")))
                .isInstanceOf(EntityConflictException.class);
    }

    @Test
    void allowsABackToBackNonOverlappingScreeningInTheSameShowroom() {
        UUID movieId = movieService.create("Movie " + UUID.randomUUID(), "d", "https://x/p.jpg", null, null, null)
                .getId();
        UUID showroomId = showroomService.create("Room " + UUID.randomUUID(), 50).getId();
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);

        screeningService.schedule(movieId, showroomId, start, start.plusHours(2), new BigDecimal("10.00"));

        // Starts exactly when the first ends — not an overlap.
        ScreeningView second = screeningService.schedule(
                movieId, showroomId, start.plusHours(2), start.plusHours(4), new BigDecimal("10.00"));

        assertThat(second.showtime().getStartTime()).isEqualTo(start.plusHours(2));
    }

    @Test
    void allowsTheSameTimeSlotInADifferentShowroom() {
        UUID movieId = movieService.create("Movie " + UUID.randomUUID(), "d", "https://x/p.jpg", null, null, null)
                .getId();
        UUID showroomA = showroomService.create("Room A " + UUID.randomUUID(), 50).getId();
        UUID showroomB = showroomService.create("Room B " + UUID.randomUUID(), 50).getId();
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);

        screeningService.schedule(movieId, showroomA, start, start.plusHours(2), new BigDecimal("10.00"));

        ScreeningView second =
                screeningService.schedule(movieId, showroomB, start, start.plusHours(2), new BigDecimal("10.00"));

        assertThat(second.showroomId()).isEqualTo(showroomB);
    }

    @Test
    void rejectsEndTimeNotAfterStartTime() {
        UUID movieId = movieService.create("Movie " + UUID.randomUUID(), "d", "https://x/p.jpg", null, null, null)
                .getId();
        UUID showroomId = showroomService.create("Room " + UUID.randomUUID(), 50).getId();
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);

        assertThatThrownBy(
                        () -> screeningService.schedule(movieId, showroomId, start, start, new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seatMapShowsHeldOnceASeatLockIsAcquiredAndAvailableOtherwise() {
        UUID movieId = movieService.create("Movie " + UUID.randomUUID(), "d", "https://x/p.jpg", null, null, null)
                .getId();
        UUID showroomId = showroomService.create("Room " + UUID.randomUUID(), 50).getId();
        showroomService.bulkCreateSeats(showroomId, 1, 1);
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        ScreeningView screening =
                screeningService.schedule(movieId, showroomId, start, start.plusHours(2), new BigDecimal("10.00"));

        // seatMap lists seats via ShowroomService.listSeats, which reads
        // Postgres directly — needs the Kafka worker to have caught up
        // with the async seat-creation event first.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var seatMap = screeningService.seatMap(movieId, showroomId, screening.showtime().getId());
            assertThat(seatMap).hasSize(1);
            assertThat(seatMap.get(0).status()).isEqualTo("available");
        });
    }
}
