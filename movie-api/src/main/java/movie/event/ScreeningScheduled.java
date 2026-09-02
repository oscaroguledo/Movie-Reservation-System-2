package movie.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScreeningScheduled(
        UUID showtimeId, UUID movieId, UUID showroomId, OffsetDateTime startTime, OffsetDateTime endTime,
        BigDecimal price)
        implements MovieEvent {

    @Override
    public String key() {
        return showroomId.toString();
    }
}
