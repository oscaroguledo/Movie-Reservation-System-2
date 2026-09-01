package movie.event;

/**
 * Domain events published to the {@code movie-events} Kafka topic and
 * consumed by {@link MovieEventWorker}, which persists them to Postgres
 * as source of truth. Grows one permitted type per entity as each is
 * ported (starting with Genre) rather than all at once.
 */
public sealed interface MovieEvent permits GenreCreated, GenreUpdated, GenreDeleted {

    /** Kafka partition key — keeps events for the same entity ordered. */
    String key();
}
