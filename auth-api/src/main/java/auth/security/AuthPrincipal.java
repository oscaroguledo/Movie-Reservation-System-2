package auth.security;

import java.time.Instant;
import java.util.UUID;

/** The authenticated caller, built from a validated JWT's claims. */
public record AuthPrincipal(UUID userId, String email, String userType, String jti, Instant expiresAt) {
}
