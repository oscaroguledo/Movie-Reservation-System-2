package movie.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Snapshot of the booker's role at reservation time — mirrors
 * auth-api's UserType plus GUEST for unauthenticated bookings.
 */
public enum ReservationUserType {
    ADMIN("admin"),
    REGULAR("regular"),
    GUEST("guest");

    private final String value;

    ReservationUserType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ReservationUserType fromValue(String value) {
        for (ReservationUserType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown reservation user type: " + value);
    }

    @Converter(autoApply = true)
    static class ReservationUserTypeAttributeConverter
            implements AttributeConverter<ReservationUserType, String> {
        @Override
        public String convertToDatabaseColumn(ReservationUserType attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public ReservationUserType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReservationUserType.fromValue(dbData);
        }
    }
}
