package movie.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.Showtime;

public interface ShowtimeRepository extends JpaRepository<Showtime, UUID> {

    List<Showtime> findByStartTimeBetween(OffsetDateTime start, OffsetDateTime end);
}
