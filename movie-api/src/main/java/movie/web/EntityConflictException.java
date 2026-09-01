package movie.web;

/**
 * Generic 409 for any business-rule conflict (duplicate name, overlapping
 * screening, seat unavailable, ...) — matches the Python reference's
 * broad use of {@code ValueError} -> 409 across create/update/delete.
 */
public class EntityConflictException extends RuntimeException {

    public EntityConflictException(String message) {
        super(message);
    }
}
