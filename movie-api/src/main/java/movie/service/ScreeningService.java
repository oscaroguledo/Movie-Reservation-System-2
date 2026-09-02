package movie.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import movie.cache.ScreeningCacheService;
import movie.cache.SeatLockService;
import movie.event.MovieEventPublisher;
import movie.event.ScreeningDeleted;
import movie.event.ScreeningScheduled;
import movie.model.ShowroomSeat;
import movie.model.Showtime;
import movie.repository.MovieShowtimeRepository;
import movie.repository.ReservationRepository;
import movie.repository.ShowtimeRepository;
import movie.web.EntityConflictException;
import movie.web.EntityNotFoundException;

/**
 * Overlap prevention is a short-lived Redis lock around a
 * check-then-append against the showroom's own schedule — not a
 * Postgres exclusion constraint. Lock-acquisition failure (someone else
 * is scheduling in this showroom right now) is itself surfaced as an
 * "overlapping" conflict, matching the Python reference exactly.
 */
@Service
public class ScreeningService {

    private final ScreeningCacheService screeningCacheService;
    private final SeatLockService seatLockService;
    private final ShowtimeRepository showtimeRepository;
    private final MovieShowtimeRepository movieShowtimeRepository;
    private final ReservationRepository reservationRepository;
    private final MovieEventPublisher eventPublisher;
    private final MovieService movieService;
    private final ShowroomService showroomService;

    public ScreeningService(
            ScreeningCacheService screeningCacheService,
            SeatLockService seatLockService,
            ShowtimeRepository showtimeRepository,
            MovieShowtimeRepository movieShowtimeRepository,
            ReservationRepository reservationRepository,
            MovieEventPublisher eventPublisher,
            MovieService movieService,
            ShowroomService showroomService) {
        this.screeningCacheService = screeningCacheService;
        this.seatLockService = seatLockService;
        this.showtimeRepository = showtimeRepository;
        this.movieShowtimeRepository = movieShowtimeRepository;
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
        this.movieService = movieService;
        this.showroomService = showroomService;
    }

    public ScreeningView schedule(
            UUID movieId, UUID showroomId, OffsetDateTime startTime, OffsetDateTime endTime, BigDecimal price) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }

        // Ensure the movie and showroom actually exist first — a 404 for
        // either should win over a lock-contention/overlap 409. Also
        // gives us the Movie to build the response with, without an
        // extra lookup that would race the async Postgres persistence.
        var movie = movieService.getById(movieId);
        showroomService.getById(showroomId);

        if (!screeningCacheService.lockSchedule(showroomId)) {
            throw new EntityConflictException(
                    "This showroom already has a screening scheduled in that time window");
        }
        try {
            for (var interval : screeningCacheService.getSchedule(showroomId)) {
                if (interval.start().isBefore(endTime) && interval.end().isAfter(startTime)) {
                    throw new EntityConflictException(
                            "This showroom already has a screening scheduled in that time window");
                }
            }

            UUID showtimeId = UUID.randomUUID();
            screeningCacheService.addToSchedule(showroomId, showtimeId, startTime, endTime);
            screeningCacheService.markScreening(movieId, showroomId, showtimeId);

            Showtime showtime = new Showtime(showtimeId, startTime, endTime, price);
            screeningCacheService.saveShowtime(showtime);

            eventPublisher.publish(new ScreeningScheduled(showtimeId, movieId, showroomId, startTime, endTime, price));
            return new ScreeningView(movie, showtime, showroomId);
        } finally {
            screeningCacheService.unlockSchedule(showroomId);
        }
    }

    public Showtime getShowtime(UUID showtimeId) {
        return screeningCacheService
                .getShowtime(showtimeId)
                .or(() -> showtimeRepository.findById(showtimeId))
                .orElseThrow(() -> new EntityNotFoundException("Showtime not found: " + showtimeId));
    }

    /** Direct Postgres reads (see ScreeningCacheService's class javadoc for why). */
    public List<ScreeningView> listForDate(LocalDate date) {
        OffsetDateTime start = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<ScreeningView> results = new ArrayList<>();
        for (Showtime showtime : showtimeRepository.findByStartTimeBetween(start, end)) {
            for (var ms : movieShowtimeRepository.findByShowtimeId(showtime.getId())) {
                results.add(new ScreeningView(movieService.getById(ms.getMovieId()), showtime, ms.getShowroomId()));
            }
        }
        return sortedByStartTime(results);
    }

    /** Cross-date browse by movie and/or showroom — at least one filter is required. */
    public List<ScreeningView> listUpcoming(UUID movieId, UUID showroomId) {
        List<movie.model.MovieShowtime> candidates;
        if (movieId != null && showroomId != null) {
            candidates = movieShowtimeRepository.findByMovieId(movieId).stream()
                    .filter(ms -> ms.getShowroomId().equals(showroomId))
                    .toList();
        } else if (movieId != null) {
            candidates = movieShowtimeRepository.findByMovieId(movieId);
        } else {
            candidates = movieShowtimeRepository.findByShowroomId(showroomId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<ScreeningView> results = new ArrayList<>();
        for (var ms : candidates) {
            Showtime showtime = getShowtime(ms.getShowtimeId());
            if (showtime.getStartTime().isAfter(now)) {
                results.add(new ScreeningView(movieService.getById(ms.getMovieId()), showtime, ms.getShowroomId()));
            }
        }
        return sortedByStartTime(results);
    }

    public void delete(UUID movieId, UUID showroomId, UUID showtimeId) {
        if (!screeningExists(movieId, showroomId, showtimeId)) {
            throw new EntityNotFoundException("Screening not found");
        }
        if (seatLockService.hasAnyActiveSeat(showtimeId)) {
            throw new EntityConflictException("Cannot delete a screening with active reservations");
        }
        if (seatLockService.hasReservationHistory(showtimeId)
                || reservationRepository.existsByMovieIdAndShowroomIdAndShowtimeId(movieId, showroomId, showtimeId)) {
            throw new EntityConflictException("Cannot delete a screening with reservation history");
        }

        screeningCacheService.unmarkScreening(movieId, showroomId, showtimeId);
        screeningCacheService.removeFromSchedule(showroomId, showtimeId);
        eventPublisher.publish(new ScreeningDeleted(movieId, showroomId, showtimeId));
    }

    /**
     * Seat status is "available"/"held" for now — distinguishing "held"
     * (pending) from "booked" (confirmed) needs the reservation
     * cache-aside layer, added in the next step.
     */
    public List<SeatStatus> seatMap(UUID movieId, UUID showroomId, UUID showtimeId) {
        if (!screeningExists(movieId, showroomId, showtimeId)) {
            throw new EntityNotFoundException("Screening not found");
        }

        List<ShowroomSeat> seats = showroomService.listSeats(showroomId);
        List<SeatStatus> result = new ArrayList<>();
        for (ShowroomSeat seat : seats) {
            String status = seatLockService.getSeatHolder(showtimeId, seat.getId()) == null ? "available" : "held";
            result.add(new SeatStatus(seat, status));
        }
        return result;
    }

    private boolean screeningExists(UUID movieId, UUID showroomId, UUID showtimeId) {
        return screeningCacheService.screeningExists(movieId, showroomId, showtimeId)
                || movieShowtimeRepository.existsByMovieIdAndShowroomIdAndShowtimeId(movieId, showroomId, showtimeId);
    }

    private static List<ScreeningView> sortedByStartTime(List<ScreeningView> views) {
        return views.stream().sorted(Comparator.comparing(v -> v.showtime().getStartTime())).toList();
    }
}
