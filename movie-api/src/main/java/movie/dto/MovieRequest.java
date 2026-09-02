package movie.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record MovieRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String posterImageUrl,
        LocalDate releaseDate,
        Integer durationMinutes,
        List<UUID> genreIds) {
}
