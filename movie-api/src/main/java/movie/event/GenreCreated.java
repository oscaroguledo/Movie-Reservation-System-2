package movie.event;

import java.util.UUID;

public record GenreCreated(UUID genreId, String name) implements MovieEvent {

    @Override
    public String key() {
        return genreId.toString();
    }
}
