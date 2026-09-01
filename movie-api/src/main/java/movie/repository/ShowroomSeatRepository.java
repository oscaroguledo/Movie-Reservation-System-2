package movie.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.ShowroomSeat;

public interface ShowroomSeatRepository extends JpaRepository<ShowroomSeat, UUID> {

    List<ShowroomSeat> findByShowroomId(UUID showroomId);

    boolean existsByShowroomIdAndRowAndNumber(UUID showroomId, String row, Integer number);
}
