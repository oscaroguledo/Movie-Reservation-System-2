package movie.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import movie.cache.ReservationCacheService;
import movie.cache.SeatLockService;
import movie.event.MovieEventPublisher;
import movie.event.ReservationCancelled;
import movie.event.ReservationConfirmed;
import movie.event.ReservationCreated;
import movie.event.ReservationExpired;
import movie.model.Reservation;
import movie.model.ReservationStatus;
import movie.model.ReservationUserType;
import movie.model.Showtime;
import movie.repository.ReservationRepository;
import movie.security.MoviePrincipal;
import movie.web.EntityConflictException;
import movie.web.EntityNotFoundException;
import movie.web.NotAuthorizedException;
import movie.web.PaymentFailedException;

/**
 * {@code SeatLockService.acquireSeat}'s SETNX is the overbooking
 * guarantee — Postgres's unique index (see the migration) is a
 * secondary backstop, not the live guard.
 */
@Service
public class ReservationService {

    private final ReservationCacheService reservationCacheService;
    private final SeatLockService seatLockService;
    private final ReservationRepository reservationRepository;
    private final MovieEventPublisher eventPublisher;
    private final ScreeningService screeningService;
    private final PaymentService paymentService;
    private final Duration holdTtl;

    public ReservationService(
            ReservationCacheService reservationCacheService,
            SeatLockService seatLockService,
            ReservationRepository reservationRepository,
            MovieEventPublisher eventPublisher,
            ScreeningService screeningService,
            PaymentService paymentService,
            @Value("${movie.reservation.hold-ttl-seconds}") long holdTtlSeconds) {
        this.reservationCacheService = reservationCacheService;
        this.seatLockService = seatLockService;
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
        this.screeningService = screeningService;
        this.paymentService = paymentService;
        this.holdTtl = Duration.ofSeconds(holdTtlSeconds);
    }

    public static boolean isAuthorized(MoviePrincipal principal, Reservation reservation) {
        boolean isOwner = reservation.getUserId() != null && reservation.getUserId().equals(principal.userId());
        boolean isAdmin = principal.type() == ReservationUserType.ADMIN;
        return isOwner || isAdmin;
    }

    /** A guest hold has no identity to check against — the id itself is its only credential. */
    public static boolean canAccess(MoviePrincipal principal, Reservation reservation) {
        return reservation.getUserId() == null || isAuthorized(principal, reservation);
    }

    public List<Reservation> createHold(
            MoviePrincipal principal, UUID movieId, UUID showroomId, UUID showtimeId, List<UUID> seatIds) {
        List<UUID> reservationIds = seatIds.stream().map(id -> UUID.randomUUID()).toList();
        List<UUID> acquired = new ArrayList<>();

        for (int i = 0; i < seatIds.size(); i++) {
            UUID seatId = seatIds.get(i);
            boolean got = seatLockService.acquireSeat(showtimeId, seatId, reservationIds.get(i), holdTtl);
            if (!got) {
                for (UUID releasedSeatId : acquired) {
                    seatLockService.releaseSeat(showtimeId, releasedSeatId);
                }
                throw new EntityConflictException("One or more selected seats are no longer available");
            }
            acquired.add(seatId);
        }

        seatLockService.markReservationHistory(showtimeId);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plus(holdTtl);

        List<Reservation> reservations = new ArrayList<>();
        for (int i = 0; i < seatIds.size(); i++) {
            Reservation reservation = new Reservation(
                    reservationIds.get(i), principal.userId(), principal.type(), movieId, showroomId, showtimeId,
                    seatIds.get(i), ReservationStatus.PENDING, expiresAt);
            reservationCacheService.put(reservation);
            reservations.add(reservation);
            eventPublisher.publish(new ReservationCreated(
                    reservation.getId(), principal.userId(), principal.type(), movieId, showroomId, showtimeId,
                    seatIds.get(i), ReservationStatus.PENDING, expiresAt));
        }
        return reservations;
    }

    public Reservation get(UUID reservationId) {
        return getAndMaybeExpire(reservationId);
    }

    public Reservation getForPrincipal(MoviePrincipal principal, UUID reservationId) {
        Reservation reservation = getAndMaybeExpire(reservationId);
        if (reservation == null) {
            return null;
        }
        if (!canAccess(principal, reservation)) {
            throw new NotAuthorizedException("Not authorized to view this reservation");
        }
        return reservation;
    }

    public Reservation confirm(MoviePrincipal principal, UUID reservationId, BigDecimal amount, String providerReference) {
        Reservation reservation = getAndMaybeExpire(reservationId);
        if (reservation == null) {
            return null;
        }
        if (!canAccess(principal, reservation)) {
            throw new NotAuthorizedException("Not authorized to confirm this reservation");
        }
        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new EntityConflictException("This hold has expired");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new EntityConflictException("Only a pending reservation can be confirmed");
        }

        Showtime showtime = screeningService.getShowtime(reservation.getShowtimeId());
        var payment = paymentService.charge(reservationId, amount, showtime.getPrice(), providerReference);
        if (payment.getStatus() != movie.model.PaymentStatus.SUCCEEDED) {
            throw new PaymentFailedException(
                    "Payment of " + amount + " does not match the reservation price of " + showtime.getPrice());
        }

        reservation.applyStatus(ReservationStatus.CONFIRMED, null);
        reservationCacheService.put(reservation);
        seatLockService.persistSeat(reservation.getShowtimeId(), reservation.getShowroomSeatId());
        eventPublisher.publish(toConfirmedEvent(reservation));
        return reservation;
    }

    public Reservation cancel(MoviePrincipal principal, UUID reservationId) {
        Reservation reservation = getAndMaybeExpire(reservationId);
        if (reservation == null) {
            return null;
        }
        if (!canAccess(principal, reservation)) {
            throw new NotAuthorizedException("Not authorized to cancel this reservation");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new EntityConflictException("Only a pending or confirmed reservation can be cancelled");
        }

        Showtime showtime = tryGetShowtime(reservation.getShowtimeId());
        if (showtime != null && !showtime.getStartTime().isAfter(OffsetDateTime.now())) {
            throw new EntityConflictException("Cannot cancel a reservation for a screening that already started");
        }

        boolean wasConfirmed = reservation.getStatus() == ReservationStatus.CONFIRMED;
        reservation.applyStatus(ReservationStatus.CANCELLED, null);
        reservationCacheService.put(reservation);
        seatLockService.releaseSeat(reservation.getShowtimeId(), reservation.getShowroomSeatId());
        if (wasConfirmed && showtime != null) {
            paymentService.refund(reservationId, showtime.getPrice(), null);
        }
        eventPublisher.publish(toCancelledEvent(reservation));
        return reservation;
    }

    public List<Reservation> listForPrincipal(MoviePrincipal principal) {
        if (principal.isGuest()) {
            return List.of();
        }
        return reservationCacheService.listIdsForUser(principal.userId()).stream()
                .map(this::getAndMaybeExpire)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Lazy expiry: settle a stale PENDING hold on read rather than relying on a background sweep. */
    private Reservation getAndMaybeExpire(UUID reservationId) {
        Reservation reservation = reservationCacheService
                .get(reservationId)
                .or(() -> {
                    var fromDb = reservationRepository.findById(reservationId);
                    fromDb.ifPresent(reservationCacheService::put);
                    return fromDb;
                })
                .orElse(null);
        if (reservation == null) {
            return null;
        }

        if (reservation.getStatus() == ReservationStatus.PENDING
                && reservation.getExpiresAt() != null
                && reservation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            reservation.applyStatus(ReservationStatus.EXPIRED, null);
            reservationCacheService.put(reservation);
            seatLockService.releaseSeat(reservation.getShowtimeId(), reservation.getShowroomSeatId());
            eventPublisher.publish(toExpiredEvent(reservation));
        }

        return reservation;
    }

    private Showtime tryGetShowtime(UUID showtimeId) {
        try {
            return screeningService.getShowtime(showtimeId);
        } catch (EntityNotFoundException e) {
            return null;
        }
    }

    private static ReservationConfirmed toConfirmedEvent(Reservation r) {
        return new ReservationConfirmed(
                r.getId(), r.getUserId(), r.getUserType(), r.getMovieId(), r.getShowroomId(), r.getShowtimeId(),
                r.getShowroomSeatId(), r.getStatus(), r.getExpiresAt());
    }

    private static ReservationCancelled toCancelledEvent(Reservation r) {
        return new ReservationCancelled(
                r.getId(), r.getUserId(), r.getUserType(), r.getMovieId(), r.getShowroomId(), r.getShowtimeId(),
                r.getShowroomSeatId(), r.getStatus(), r.getExpiresAt());
    }

    private static ReservationExpired toExpiredEvent(Reservation r) {
        return new ReservationExpired(
                r.getId(), r.getUserId(), r.getUserType(), r.getMovieId(), r.getShowroomId(), r.getShowtimeId(),
                r.getShowroomSeatId(), r.getStatus(), r.getExpiresAt());
    }
}
