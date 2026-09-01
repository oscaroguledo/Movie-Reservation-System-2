package movie.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByUserId(UUID userId);

    boolean existsByMovieIdAndShowroomIdAndShowtimeId(UUID movieId, UUID showroomId, UUID showtimeId);
}
