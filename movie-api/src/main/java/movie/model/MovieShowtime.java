package movie.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * The junction identifying one screening: this movie, in this showroom,
 * at this showtime. {@link movie.model.Reservation}'s movie/showroom/
 * showtime columns have a composite foreign key into this table,
 * enforced at the Postgres DDL level (see the migration) rather than
 * modeled as a JPA relationship — it's pure referential integrity, not
 * a navigable association the app needs to traverse in memory.
 */
@Entity
@IdClass(MovieShowtimeId.class)
@Table(name = "movie_showtimes", schema = "movie_api")
public class MovieShowtime {

    @Id
    @Column(name = "movie_id")
    private UUID movieId;

    @Id
    @Column(name = "showroom_id")
    private UUID showroomId;

    @Id
    @Column(name = "showtime_id")
    private UUID showtimeId;

    protected MovieShowtime() {
        // for JPA
    }

    public MovieShowtime(UUID movieId, UUID showroomId, UUID showtimeId) {
        this.movieId = movieId;
        this.showroomId = showroomId;
        this.showtimeId = showtimeId;
    }

    public UUID getMovieId() {
        return movieId;
    }

    public UUID getShowroomId() {
        return showroomId;
    }

    public UUID getShowtimeId() {
        return showtimeId;
    }
}
