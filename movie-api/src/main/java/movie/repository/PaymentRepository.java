package movie.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import movie.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByReservationId(UUID reservationId);
}
