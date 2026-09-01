package auth.dto;

import jakarta.validation.constraints.Size;

/** All fields optional (PATCH semantics) — null means "leave unchanged". */
public record UpdateUserRequest(
        String firstName,
        String lastName,
        @Size(min = 8, message = "must be at least 8 characters") String password) {
}
