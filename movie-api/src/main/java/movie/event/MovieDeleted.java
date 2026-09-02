package movie.event;

import java.util.UUID;

public record MovieDeleted(UUID movieId) implements MovieEvent {

    @Override
    public String key() {
        return movieId.toString();
    }
}
