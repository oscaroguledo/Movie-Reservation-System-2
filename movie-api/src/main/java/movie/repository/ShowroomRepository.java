package movie.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.Showroom;

public interface ShowroomRepository extends JpaRepository<Showroom, UUID> {

    boolean existsByName(String name);
}
