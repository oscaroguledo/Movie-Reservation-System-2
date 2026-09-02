package movie.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.MovieShowtime;
import movie.model.MovieShowtimeId;

public interface MovieShowtimeRepository extends JpaRepository<MovieShowtime, MovieShowtimeId> {

    boolean existsByMovieIdAndShowroomIdAndShowtimeId(UUID movieId, UUID showroomId, UUID showtimeId);

    void deleteByMovieIdAndShowroomIdAndShowtimeId(UUID movieId, UUID showroomId, UUID showtimeId);

    List<MovieShowtime> findByShowtimeId(UUID showtimeId);

    List<MovieShowtime> findByMovieId(UUID movieId);

    List<MovieShowtime> findByShowroomId(UUID showroomId);
}
