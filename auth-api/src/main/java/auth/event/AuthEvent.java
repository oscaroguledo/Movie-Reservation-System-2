package auth.event;

/**
 * Domain events published to the {@code auth-events} Kafka topic and
 * consumed by {@link AuthEventWorker}, which persists them to Postgres as
 * source of truth — the async half of the write-first-to-Redis,
 * publish-to-Kafka pipeline described in the port plan.
 */
public sealed interface AuthEvent permits UserRegistered, UserUpdated, UserDeleted, TokenRevoked {

    /** Kafka partition key — keeps events for the same entity ordered. */
    String key();
}
