package auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * All fields optional (PATCH semantics) — null means "leave unchanged".
 * {@code @Size}/{@code @Pattern} both treat null as valid per the Bean
 * Validation spec, so a null password still passes.
 */
public record UpdateUserRequest(
        String firstName,
        String lastName,
        @Size(min = 8, max = 128, message = "must be 8-128 characters")
                @Pattern(regexp = PasswordPolicy.REGEXP, message = PasswordPolicy.MESSAGE)
                String password) {
}
