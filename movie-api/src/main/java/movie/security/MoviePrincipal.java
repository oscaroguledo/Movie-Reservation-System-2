package movie.security;

import java.util.UUID;

import movie.model.ReservationUserType;

/**
 * Who's making the request, derived entirely from the JWT's own claims
 * (or a synthetic GUEST when there's no token) — movie-api has no
 * access to auth-api's user table to re-fetch from.
 */
public record MoviePrincipal(UUID userId, ReservationUserType type) {

    public static final MoviePrincipal GUEST = new MoviePrincipal(null, ReservationUserType.GUEST);

    public boolean isGuest() {
        return userId == null;
    }
}
