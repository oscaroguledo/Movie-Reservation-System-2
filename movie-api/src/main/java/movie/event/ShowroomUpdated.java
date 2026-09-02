package movie.event;

import java.util.UUID;

public record ShowroomUpdated(UUID showroomId, String name, Integer capacity) implements MovieEvent {

    @Override
    public String key() {
        return showroomId.toString();
    }
}
