package movie.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import movie.model.ReservationStatus;
import movie.model.ReservationUserType;

/**
 * Carries the full reservation snapshot, not just id+status — matching
 * the Python reference's status-change events (it publishes the whole
 * reservation dict). The worker's "update, or create if the CREATE
 * event hasn't landed yet" fallback needs every field to create the row
 * correctly, not just what changed.
 */
public record ReservationConfirmed(
        UUID reservationId,
        UUID userId,
        ReservationUserType userType,
        UUID movieId,
        UUID showroomId,
        UUID showtimeId,
        UUID showroomSeatId,
        ReservationStatus status,
        OffsetDateTime expiresAt)
        implements MovieEvent {

    @Override
    public String key() {
        return reservationId.toString();
    }
}
