package movie.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.Showtime;

public interface ShowtimeRepository extends JpaRepository<Showtime, UUID> {
}
