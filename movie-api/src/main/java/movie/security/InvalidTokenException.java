package movie.security;

/** Thrown for a token that fails signature/expiry/claim checks. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
