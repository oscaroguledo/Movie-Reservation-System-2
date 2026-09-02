package movie.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import movie.model.Genre;

public record GenreResponse(UUID id, String name, OffsetDateTime createdAt) {

    public static GenreResponse from(Genre genre) {
        return new GenreResponse(genre.getId(), genre.getName(), genre.getCreatedAt());
    }
}
