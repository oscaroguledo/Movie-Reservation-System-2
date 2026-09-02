package movie.event;

import java.util.UUID;

public record ShowroomDeleted(UUID showroomId) implements MovieEvent {

    @Override
    public String key() {
        return showroomId.toString();
    }
}
