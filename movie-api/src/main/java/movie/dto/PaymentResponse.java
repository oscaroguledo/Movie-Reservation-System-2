package movie.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import movie.model.Payment;

public record PaymentResponse(
        UUID id, UUID reservationId, BigDecimal amount, String status, String providerReference,
        OffsetDateTime createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getReservationId(), p.getAmount(), p.getStatus().getValue(), p.getProviderReference(),
                p.getCreatedAt());
    }
}
