package movie.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import movie.model.PaymentStatus;
import movie.model.Reservation;
import movie.model.ReservationStatus;
import movie.repository.PaymentRepository;
import movie.repository.ReservationRepository;
import movie.repository.ShowroomSeatRepository;
import movie.web.EntityNotFoundException;

/** Admin-only, direct Postgres reads — reporting-style, not cache-aside, same as every other listing in this app. */
@Service
public class ReportingService {

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final ShowroomSeatRepository showroomSeatRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ReportingService(
            ReservationRepository reservationRepository, PaymentRepository paymentRepository,
            ShowroomSeatRepository showroomSeatRepository) {
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.showroomSeatRepository = showroomSeatRepository;
    }

    @Transactional(readOnly = true)
    public List<Reservation> allReservations(ReservationStatus status, int limit, int offset) {
        StringBuilder jpql = new StringBuilder("SELECT r FROM Reservation r WHERE 1=1");
        if (status != null) {
            jpql.append(" AND r.status = :status");
        }
        jpql.append(" ORDER BY r.createdAt DESC");

        TypedQuery<Reservation> query = entityManager.createQuery(jpql.toString(), Reservation.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public ScreeningCapacity screeningCapacity(UUID movieId, UUID showroomId, UUID showtimeId) {
        int totalSeats = showroomSeatRepository.findByShowroomId(showroomId).size();
        if (totalSeats == 0) {
            throw new EntityNotFoundException("Screening not found");
        }
        long booked = reservationRepository.countByMovieIdAndShowroomIdAndShowtimeIdAndStatus(
                movieId, showroomId, showtimeId, ReservationStatus.CONFIRMED);
        long held = reservationRepository.countByMovieIdAndShowroomIdAndShowtimeIdAndStatus(
                movieId, showroomId, showtimeId, ReservationStatus.PENDING);
        return new ScreeningCapacity(totalSeats, booked, held, totalSeats - booked - held);
    }

    public Revenue revenue() {
        BigDecimal gross = paymentRepository.sumAmountByStatus(PaymentStatus.SUCCEEDED);
        BigDecimal refunded = paymentRepository.sumAmountByStatus(PaymentStatus.REFUNDED);
        return new Revenue(gross, refunded, gross.subtract(refunded));
    }

    public record ScreeningCapacity(int totalSeats, long booked, long held, long available) {
    }

    public record Revenue(BigDecimal gross, BigDecimal refunded, BigDecimal net) {
    }
}
