package movie.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ScreeningRequest(
        @NotNull UUID movieId,
        @NotNull UUID showroomId,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotNull @DecimalMin("0.0") BigDecimal price) {
}
