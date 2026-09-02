package movie.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import movie.model.ReservationStatus;
import movie.model.ReservationUserType;

/** See {@link ReservationConfirmed}'s javadoc for why every field is carried, not just the status. */
public record ReservationExpired(
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
