package movie.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import movie.IntegrationTestSupport;
import movie.repository.GenreRepository;

/**
 * Boots the real Spring context against throwaway Postgres + Kafka
 * containers, publishes each event kind, and awaits {@link
 * MovieEventWorker} actually persisting it — mirrors auth-api's
 * AuthEventWorkerIntegrationTest.
 */
@SpringBootTest
class MovieEventWorkerIntegrationTest extends IntegrationTestSupport {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MovieEventPublisher publisher;

    @Autowired
    private GenreRepository genreRepository;

    @Test
    void aGenreCreatedEventIsEventuallyPersisted() {
        UUID genreId = UUID.randomUUID();

        publisher.publish(new GenreCreated(genreId, "Async Genre " + genreId));

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            var found = genreRepository.findById(genreId);
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Async Genre " + genreId);
        });
    }

    @Test
    void aGenreUpdatedEventIsEventuallyReflected() {
        UUID genreId = UUID.randomUUID();
        publisher.publish(new GenreCreated(genreId, "Before"));
        await().atMost(AWAIT_TIMEOUT).until(() -> genreRepository.existsById(genreId));

        publisher.publish(new GenreUpdated(genreId, "After"));

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            var found = genreRepository.findById(genreId);
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("After");
        });
    }

    @Test
    void anUpdateArrivingBeforeItsCreateStillCreatesTheRow() {
        // Simulates Kafka redelivery/ordering putting an UPDATE ahead of
        // its CREATE — the worker must fall back to creating the row.
        UUID genreId = UUID.randomUUID();

        publisher.publish(new GenreUpdated(genreId, "Created via update"));

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            var found = genreRepository.findById(genreId);
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Created via update");
        });
    }

    @Test
    void aGenreDeletedEventIsEventuallyApplied() {
        UUID genreId = UUID.randomUUID();
        publisher.publish(new GenreCreated(genreId, "To delete"));
        await().atMost(AWAIT_TIMEOUT).until(() -> genreRepository.existsById(genreId));

        publisher.publish(new GenreDeleted(genreId));

        await().atMost(AWAIT_TIMEOUT).until(() -> !genreRepository.existsById(genreId));
    }
}
