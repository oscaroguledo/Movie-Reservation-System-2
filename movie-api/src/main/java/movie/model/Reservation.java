package movie.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A user's hold or booking on one seat for one screening. Note: unlike
 * auth-api's entities, this is Postgres's durable-history copy of state
 * whose live source of truth is Redis (the seat lock) — see
 * ReservationService. userId is null for GUEST bookings; there's no
 * DB-level FK to auth_api.users (movie_api doesn't own that table).
 */
@Entity
@Table(name = "reservations", schema = "movie_api")
public class Reservation {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_type", nullable = false, length = 20)
    private ReservationUserType userType;

    @Column(name = "movie_id", nullable = false)
    private UUID movieId;

    @Column(name = "showroom_id", nullable = false)
    private UUID showroomId;

    @Column(name = "showtime_id", nullable = false)
    private UUID showtimeId;

    @Column(name = "showroom_seat_id", nullable = false)
    private UUID showroomSeatId;

    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Reservation() {
        // for JPA
    }

    public Reservation(
            UUID id,
            UUID userId,
            ReservationUserType userType,
            UUID movieId,
            UUID showroomId,
            UUID showtimeId,
            UUID showroomSeatId,
            ReservationStatus status,
            OffsetDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.userType = userType;
        this.movieId = movieId;
        this.showroomId = showroomId;
        this.showtimeId = showtimeId;
        this.showroomSeatId = showroomSeatId;
        this.status = status;
        this.expiresAt = expiresAt;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void applyStatus(ReservationStatus status, OffsetDateTime expiresAt) {
        this.status = status;
        this.expiresAt = expiresAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ReservationUserType getUserType() {
        return userType;
    }

    public UUID getMovieId() {
        return movieId;
    }

    public UUID getShowroomId() {
        return showroomId;
    }

    public UUID getShowtimeId() {
        return showtimeId;
    }

    public UUID getShowroomSeatId() {
        return showroomSeatId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
