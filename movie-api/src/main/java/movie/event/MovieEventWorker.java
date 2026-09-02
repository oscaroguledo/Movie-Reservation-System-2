package movie.event;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import movie.model.Genre;
import movie.model.Movie;
import movie.model.MovieShowtime;
import movie.model.Showroom;
import movie.model.ShowroomSeat;
import movie.model.Showtime;
import movie.repository.GenreRepository;
import movie.repository.MovieRepository;
import movie.repository.MovieShowtimeRepository;
import movie.repository.ShowroomRepository;
import movie.repository.ShowroomSeatRepository;
import movie.repository.ShowtimeRepository;

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
    private final MovieRepository movieRepository;
    private final ShowroomRepository showroomRepository;
    private final ShowroomSeatRepository showroomSeatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final MovieShowtimeRepository movieShowtimeRepository;

    public MovieEventWorker(
            GenreRepository genreRepository,
            MovieRepository movieRepository,
            ShowroomRepository showroomRepository,
            ShowroomSeatRepository showroomSeatRepository,
            ShowtimeRepository showtimeRepository,
            MovieShowtimeRepository movieShowtimeRepository) {
        this.genreRepository = genreRepository;
        this.movieRepository = movieRepository;
        this.showroomRepository = showroomRepository;
        this.showroomSeatRepository = showroomSeatRepository;
        this.showtimeRepository = showtimeRepository;
        this.movieShowtimeRepository = movieShowtimeRepository;
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
            case MovieCreated e -> createMovie(e);
            case MovieUpdated e -> updateMovie(e);
            case MovieDeleted e -> movieRepository.deleteById(e.movieId());
            case ShowroomCreated e -> createShowroom(e);
            case ShowroomUpdated e -> updateShowroom(e);
            case ShowroomDeleted e -> showroomRepository.deleteById(e.showroomId());
            case ShowroomSeatsCreated e -> createShowroomSeats(e);
            case ScreeningScheduled e -> createScreening(e);
            case ScreeningDeleted e -> deleteScreening(e);
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

    private void createMovie(MovieCreated e) {
        try {
            Movie movie = new Movie(
                    e.movieId(), e.title(), e.description(), e.posterImageUrl(), e.releaseDate(),
                    e.durationMinutes());
            movie.setGenres(resolveGenres(e.genreIds()));
            movieRepository.save(movie);
        } catch (DataIntegrityViolationException ex) {
            // Redelivery of an already-applied write is expected and harmless.
        }
    }

    private void updateMovie(MovieUpdated e) {
        movieRepository
                .findById(e.movieId())
                .ifPresentOrElse(
                        movie -> {
                            movie.applyUpdate(
                                    e.title(), e.description(), e.posterImageUrl(), e.releaseDate(),
                                    e.durationMinutes());
                            movie.setGenres(resolveGenres(e.genreIds()));
                            movieRepository.save(movie);
                        },
                        () -> createMovie(new MovieCreated(
                                e.movieId(), e.title(), e.description(), e.posterImageUrl(), e.releaseDate(),
                                e.durationMinutes(), e.genreIds())));
    }

    private Set<Genre> resolveGenres(List<UUID> genreIds) {
        return genreIds == null || genreIds.isEmpty() ? Set.of() : Set.copyOf(genreRepository.findAllById(genreIds));
    }

    private void createShowroom(ShowroomCreated e) {
        try {
            showroomRepository.save(new Showroom(e.showroomId(), e.name(), e.capacity()));
        } catch (DataIntegrityViolationException ex) {
            // Redelivery of an already-applied write is expected and harmless.
        }
    }

    private void updateShowroom(ShowroomUpdated e) {
        showroomRepository
                .findById(e.showroomId())
                .ifPresentOrElse(
                        showroom -> {
                            showroom.applyUpdate(e.name(), e.capacity());
                            showroomRepository.save(showroom);
                        },
                        () -> showroomRepository.save(new Showroom(e.showroomId(), e.name(), e.capacity())));
    }

    private void createShowroomSeats(ShowroomSeatsCreated e) {
        try {
            for (ShowroomSeatsCreated.SeatData seat : e.seats()) {
                showroomSeatRepository.save(
                        new ShowroomSeat(seat.id(), e.showroomId(), seat.row(), seat.number()));
            }
        } catch (DataIntegrityViolationException ex) {
            // Redelivery of an already-applied write is expected and harmless.
        }
    }

    private void createScreening(ScreeningScheduled e) {
        try {
            showtimeRepository.save(new Showtime(e.showtimeId(), e.startTime(), e.endTime(), e.price()));
            movieShowtimeRepository.save(new MovieShowtime(e.movieId(), e.showroomId(), e.showtimeId()));
        } catch (DataIntegrityViolationException ex) {
            // Redelivery of an already-applied write is expected and harmless.
        }
    }

    private void deleteScreening(ScreeningDeleted e) {
        movieShowtimeRepository.deleteByMovieIdAndShowroomIdAndShowtimeId(
                e.movieId(), e.showroomId(), e.showtimeId());
        showtimeRepository.deleteById(e.showtimeId());
    }
}
