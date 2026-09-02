package movie.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShowroomSeatBulkCreateRequest(
        @NotNull @Min(1) @Max(26) Integer rows, @NotNull @Min(1) Integer seatsPerRow) {
}
