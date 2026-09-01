package movie.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.MovieShowtime;
import movie.model.MovieShowtimeId;

public interface MovieShowtimeRepository extends JpaRepository<MovieShowtime, MovieShowtimeId> {

    boolean existsByMovieIdAndShowroomIdAndShowtimeId(UUID movieId, UUID showroomId, UUID showtimeId);
}
