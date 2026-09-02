package movie.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MovieUpdated(
        UUID movieId,
        String title,
        String description,
        String posterImageUrl,
        LocalDate releaseDate,
        Integer durationMinutes,
        List<UUID> genreIds)
        implements MovieEvent {

    @Override
    public String key() {
        return movieId.toString();
    }
}
