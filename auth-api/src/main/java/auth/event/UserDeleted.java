package auth.event;

import java.util.UUID;

public record UserDeleted(UUID userId) implements AuthEvent {

    @Override
    public String key() {
        return userId.toString();
    }
}
