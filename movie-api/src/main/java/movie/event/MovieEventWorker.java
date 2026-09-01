package movie.event;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import movie.model.Genre;
import movie.repository.GenreRepository;

/**
 * Consumes {@link MovieEvent}s and persists them to Postgres — matches
 * the Python reference's {@code worker.py} idempotent-upsert semantics
 * rather than relying on Spring Kafka's default bounded-retry (the
 * approach auth-api's worker takes): a duplicate CREATE (redelivery) is
 * caught and treated as harmless; an UPDATE landing before its CREATE
 * (possible under Kafka's at-least-once delivery) falls back to
 * creating the row in its current state rather than losing it. Only a
 * DB-unavailable failure leaves the message unacknowledged — anything
 * else still acknowledges, so one bad message can't block the partition
 * forever. Requires {@code spring.kafka.listener.ack-mode: manual*}
 * (see application.yml).
 */
@Component
public class MovieEventWorker {

    private final GenreRepository genreRepository;

    public MovieEventWorker(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    @KafkaListener(topics = MovieEventPublisher.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(MovieEvent event, Acknowledgment acknowledgment) {
        try {
            apply(event);
        } catch (DataAccessResourceFailureException e) {
            // DB unreachable — leave unacknowledged; Kafka redelivers.
            return;
        } catch (RuntimeException e) {
            // Poison message — acknowledge anyway rather than block the partition.
        }
        acknowledgment.acknowledge();
    }

    @Transactional
    void apply(MovieEvent event) {
        switch (event) {
            case GenreCreated e -> createGenre(e);
            case GenreUpdated e -> updateGenre(e);
            case GenreDeleted e -> genreRepository.deleteById(e.genreId());
        }
    }

    private void createGenre(GenreCreated e) {
        try {
            genreRepository.save(new Genre(e.genreId(), e.name()));
        } catch (DataIntegrityViolationException ex) {
            // Redelivery of an already-applied write is expected and harmless.
        }
    }

    private void updateGenre(GenreUpdated e) {
        genreRepository
                .findById(e.genreId())
                .ifPresentOrElse(
                        genre -> {
                            genre.rename(e.name());
                            genreRepository.save(genre);
                        },
                        () -> genreRepository.save(new Genre(e.genreId(), e.name())));
    }
}
