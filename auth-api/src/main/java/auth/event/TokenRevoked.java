package auth.event;

import java.time.OffsetDateTime;

public record TokenRevoked(String jti, OffsetDateTime expiresAt) implements AuthEvent {

    @Override
    public String key() {
        return jti;
    }
}
