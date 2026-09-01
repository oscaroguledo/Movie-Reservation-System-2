package movie.event;

import java.util.UUID;

public record GenreUpdated(UUID genreId, String name) implements MovieEvent {

    @Override
    public String key() {
        return genreId.toString();
    }
}
