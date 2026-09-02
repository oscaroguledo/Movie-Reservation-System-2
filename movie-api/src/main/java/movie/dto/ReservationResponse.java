package movie.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import movie.model.Reservation;

public record ReservationResponse(
        UUID id,
        UUID userId,
        String userType,
        UUID movieId,
        UUID showroomId,
        UUID showtimeId,
        UUID showroomSeatId,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getUserId(), r.getUserType().getValue(), r.getMovieId(), r.getShowroomId(),
                r.getShowtimeId(), r.getShowroomSeatId(), r.getStatus().getValue(), r.getExpiresAt(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
