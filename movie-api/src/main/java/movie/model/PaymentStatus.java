package movie.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Payments are simulated — see {@code PaymentService}: no real gateway integration. */
public enum PaymentStatus {
    PENDING("pending"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    REFUNDED("refunded");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PaymentStatus fromValue(String value) {
        for (PaymentStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment status: " + value);
    }

    @Converter(autoApply = true)
    static class PaymentStatusAttributeConverter implements AttributeConverter<PaymentStatus, String> {
        @Override
        public String convertToDatabaseColumn(PaymentStatus attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public PaymentStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : PaymentStatus.fromValue(dbData);
        }
    }
}
