package auth.dto;

/**
 * Shared password-complexity rule for {@link RegisterRequest} and
 * {@link UpdateUserRequest}, matching the Python reference's
 * {@code validate_password_complexity}: at least one uppercase letter,
 * one lowercase letter, one digit, and one special character.
 */
final class PasswordPolicy {

    static final String REGEXP =
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).*$";

    static final String MESSAGE =
            "must contain an uppercase letter, a lowercase letter, a digit, and a special character";

    private PasswordPolicy() {
    }
}
