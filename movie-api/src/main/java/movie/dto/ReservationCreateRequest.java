package movie.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Holds one or more seats for a single screening. All seats succeed or fail together. */
public record ReservationCreateRequest(
        @NotNull UUID movieId,
        @NotNull UUID showroomId,
        @NotNull UUID showtimeId,
        @NotEmpty List<UUID> showroomSeatIds) {
}
