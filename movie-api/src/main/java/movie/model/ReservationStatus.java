package movie.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * PENDING is a temporary hold on a seat, made permanent by CONFIRMED or
 * released by CANCELLED/EXPIRED — see {@code hold-ttl-seconds}.
 */
public enum ReservationStatus {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    private final String value;

    ReservationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ReservationStatus fromValue(String value) {
        for (ReservationStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown reservation status: " + value);
    }

    @Converter(autoApply = true)
    static class ReservationStatusAttributeConverter implements AttributeConverter<ReservationStatus, String> {
        @Override
        public String convertToDatabaseColumn(ReservationStatus attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public ReservationStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReservationStatus.fromValue(dbData);
        }
    }
}
