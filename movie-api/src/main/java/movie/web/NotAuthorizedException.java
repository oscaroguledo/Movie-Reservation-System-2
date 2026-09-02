package movie.web;

/** The current principal may not act on a reservation not theirs (and not a guest hold). */
public class NotAuthorizedException extends RuntimeException {

    public NotAuthorizedException(String message) {
        super(message);
    }
}
