package movie.event;

import java.util.List;
import java.util.UUID;

public record ShowroomSeatsCreated(UUID showroomId, List<SeatData> seats) implements MovieEvent {

    @Override
    public String key() {
        return showroomId.toString();
    }

    public record SeatData(UUID id, String row, Integer number) {
    }
}
