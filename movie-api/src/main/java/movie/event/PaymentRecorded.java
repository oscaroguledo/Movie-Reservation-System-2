package movie.event;

import java.math.BigDecimal;
import java.util.UUID;

import movie.model.PaymentStatus;

public record PaymentRecorded(
        UUID paymentId, UUID reservationId, BigDecimal amount, PaymentStatus status, String providerReference)
        implements MovieEvent {

    @Override
    public String key() {
        return reservationId.toString();
    }
}
