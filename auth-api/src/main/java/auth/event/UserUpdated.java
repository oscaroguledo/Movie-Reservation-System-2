package auth.event;

import java.util.UUID;

public record UserUpdated(
        UUID userId, String userType, String email, String firstName, String lastName, String passwordHash)
        implements AuthEvent {

    @Override
    public String key() {
        return userId.toString();
    }
}
