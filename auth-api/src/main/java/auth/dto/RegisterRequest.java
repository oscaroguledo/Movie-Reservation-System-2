package auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank
                @Size(min = 8, max = 128, message = "must be 8-128 characters")
                @Pattern(
                        regexp = PasswordPolicy.REGEXP,
                        message = PasswordPolicy.MESSAGE)
                String password) {
}
