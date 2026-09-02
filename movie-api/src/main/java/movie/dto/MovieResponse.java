package movie.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import movie.model.Movie;

public record MovieResponse(
        UUID id,
        String title,
        String description,
        String posterImageUrl,
        LocalDate releaseDate,
        Integer durationMinutes,
        List<GenreResponse> genres,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getPosterImageUrl(),
                movie.getReleaseDate(),
                movie.getDurationMinutes(),
                movie.getGenres().stream().map(GenreResponse::from).toList(),
                movie.getCreatedAt(),
                movie.getUpdatedAt());
    }
}
