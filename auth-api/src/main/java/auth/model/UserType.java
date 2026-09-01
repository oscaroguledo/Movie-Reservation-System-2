package auth.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the {@code user_type} enum in the Python reference schema
 * (admin, regular). Stored as lowercase text — see
 * {@code V1__init_auth_schema.sql}.
 */
public enum UserType {
    ADMIN("admin"),
    REGULAR("regular");

    private final String value;

    UserType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UserType fromValue(String value) {
        for (UserType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown user_type: " + value);
    }

    @Converter(autoApply = true)
    static class UserTypeAttributeConverter implements AttributeConverter<UserType, String> {
        @Override
        public String convertToDatabaseColumn(UserType attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public UserType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : UserType.fromValue(dbData);
        }
    }
}
