package movie.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import movie.cache.EntityCacheService;
import movie.event.MovieEventPublisher;
import movie.event.PaymentRecorded;
import movie.model.Payment;
import movie.model.PaymentStatus;
import movie.repository.PaymentRepository;
import movie.web.EntityNotFoundException;

/**
 * Payments are simulated: charge() succeeds only when the submitted
 * amount matches the reservation's price, otherwise it's recorded
 * FAILED. Append-only — charge()/refund() each write a new row.
 */
@Service
public class PaymentService {

    private static final String CACHE_PREFIX = "payment";

    private final PaymentRepository paymentRepository;
    private final EntityCacheService entityCacheService;
    private final MovieEventPublisher eventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository, EntityCacheService entityCacheService,
            MovieEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.entityCacheService = entityCacheService;
        this.eventPublisher = eventPublisher;
    }

    public Payment charge(UUID reservationId, BigDecimal amount, BigDecimal expectedAmount, String providerReference) {
        PaymentStatus status =
                amount.compareTo(expectedAmount) == 0 ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
        return record(reservationId, amount, status, providerReference);
    }

    public Payment refund(UUID reservationId, BigDecimal amount, String providerReference) {
        return record(reservationId, amount, PaymentStatus.REFUNDED, providerReference);
    }

    public Payment getById(UUID id) {
        return entityCacheService
                .get(CACHE_PREFIX, id, Payment.class)
                .or(() -> paymentRepository.findById(id))
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
    }

    /** Direct Postgres read — a reporting-style listing, like every other list() in this service layer. */
    public List<Payment> listForReservation(UUID reservationId) {
        return paymentRepository.findByReservationId(reservationId);
    }

    private Payment record(UUID reservationId, BigDecimal amount, PaymentStatus status, String providerReference) {
        Payment payment = new Payment(UUID.randomUUID(), reservationId, amount, status, providerReference);
        entityCacheService.put(CACHE_PREFIX, payment.getId(), payment);
        eventPublisher.publish(
                new PaymentRecorded(payment.getId(), reservationId, amount, status, providerReference));
        return payment;
    }
}
