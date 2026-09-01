package movie.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.Genre;

public interface GenreRepository extends JpaRepository<Genre, UUID> {

    boolean existsByName(String name);

    Optional<Genre> findByName(String name);
}
