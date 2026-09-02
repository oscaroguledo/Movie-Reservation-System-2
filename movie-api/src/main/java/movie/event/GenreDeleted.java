package movie.event;

import java.util.UUID;

public record GenreDeleted(UUID genreId) implements MovieEvent {

    @Override
    public String key() {
        return genreId.toString();
    }
}
