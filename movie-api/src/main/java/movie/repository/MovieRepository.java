package movie.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.Movie;

public interface MovieRepository extends JpaRepository<Movie, UUID> {

    List<Movie> findByGenres_Id(UUID genreId);
}
