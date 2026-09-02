package movie.web;

/** Generic "no such row" for any entity — movie-api has too many entity kinds for one class each. */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
