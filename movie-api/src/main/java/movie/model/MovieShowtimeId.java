package movie.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite id for {@link MovieShowtime}. */
public class MovieShowtimeId implements Serializable {

    private UUID movieId;
    private UUID showroomId;
    private UUID showtimeId;

    public MovieShowtimeId() {
        // for JPA
    }

    public MovieShowtimeId(UUID movieId, UUID showroomId, UUID showtimeId) {
        this.movieId = movieId;
        this.showroomId = showroomId;
        this.showtimeId = showtimeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MovieShowtimeId that)) {
            return false;
        }
        return Objects.equals(movieId, that.movieId)
                && Objects.equals(showroomId, that.showroomId)
                && Objects.equals(showtimeId, that.showtimeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(movieId, showroomId, showtimeId);
    }
}
