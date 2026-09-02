package movie.dto;

import jakarta.validation.constraints.NotBlank;

public record GenreRequest(@NotBlank String name) {
}
