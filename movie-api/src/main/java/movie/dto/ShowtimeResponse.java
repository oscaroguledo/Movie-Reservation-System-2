package movie.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import movie.model.Showtime;

public record ShowtimeResponse(
        UUID id, OffsetDateTime startTime, OffsetDateTime endTime, long durationMinutes, BigDecimal price) {

    public static ShowtimeResponse from(Showtime showtime) {
        return new ShowtimeResponse(
                showtime.getId(), showtime.getStartTime(), showtime.getEndTime(), showtime.getDurationMinutes(),
                showtime.getPrice());
    }
}
