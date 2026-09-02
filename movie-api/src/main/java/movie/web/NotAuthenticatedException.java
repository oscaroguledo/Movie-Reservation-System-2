package movie.web;

/** For endpoints a guest can't use, e.g. listing "my reservations". */
public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException(String message) {
        super(message);
    }
}
