package auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import auth.model.User;

/** Never includes the password hash. */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String userType,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUserType().getValue(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
