package movie.event;

import java.util.UUID;

public record ScreeningDeleted(UUID movieId, UUID showroomId, UUID showtimeId) implements MovieEvent {

    @Override
    public String key() {
        return showroomId.toString();
    }
}
