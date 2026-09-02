package movie.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import movie.model.ReservationStatus;
import movie.model.ReservationUserType;

public record ReservationCreated(
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
