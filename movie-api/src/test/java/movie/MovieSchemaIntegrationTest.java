package movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import movie.model.Genre;
import movie.model.Movie;
import movie.model.MovieShowtime;
import movie.model.Payment;
import movie.model.PaymentStatus;
import movie.model.Reservation;
import movie.model.ReservationStatus;
import movie.model.ReservationUserType;
import movie.model.Showroom;
import movie.model.ShowroomSeat;
import movie.model.Showtime;
import movie.repository.GenreRepository;
import movie.repository.MovieRepository;
import movie.repository.MovieShowtimeRepository;
import movie.repository.PaymentRepository;
import movie.repository.ReservationRepository;
import movie.repository.ShowroomRepository;
import movie.repository.ShowroomSeatRepository;
import movie.repository.ShowtimeRepository;

/**
 * Boots the real Spring context against a throwaway Postgres container,
 * so the Flyway migration and every entity's JPA mapping are verified
 * against each other, not just against compile-time types — mirrors
 * auth-api's AuthSchemaIntegrationTest.
 */
@SpringBootTest
class MovieSchemaIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ShowroomRepository showroomRepository;

    @Autowired
    private ShowroomSeatRepository showroomSeatRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private MovieShowtimeRepository movieShowtimeRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void migratesSchemaAndPersistsAGenre() {
        Genre saved = genreRepository.save(new Genre(UUID.randomUUID(), "Sci-Fi " + UUID.randomUUID()));

        assertThat(saved.getCreatedAt()).isNotNull();
        Optional<Genre> found = genreRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void migratesSchemaAndPersistsAMovieWithGenres() {
        Genre genre = genreRepository.save(new Genre(UUID.randomUUID(), "Action " + UUID.randomUUID()));
        Movie movie = new Movie(
                UUID.randomUUID(), "Test Movie", "A test movie", "https://example.com/poster.jpg",
                java.time.LocalDate.of(2024, 1, 1), 120);
        movie.setGenres(Set.of(genre));

        Movie saved = movieRepository.save(movie);

        Optional<Movie> found = movieRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getGenres()).extracting(Genre::getId).containsExactly(genre.getId());
    }

    @Test
    void migratesSchemaAndPersistsAShowroomAndSeats() {
        Showroom showroom = showroomRepository.save(new Showroom(UUID.randomUUID(), "Room " + UUID.randomUUID(), 50));
        ShowroomSeat seat = showroomSeatRepository.save(
                new ShowroomSeat(UUID.randomUUID(), showroom.getId(), "A", 1));

        assertThat(showroomSeatRepository.findByShowroomId(showroom.getId()))
                .extracting(ShowroomSeat::getId)
                .contains(seat.getId());
    }

    @Test
    void migratesSchemaAndPersistsAScreeningAndReservationAndPayment() {
        Genre genre = genreRepository.save(new Genre(UUID.randomUUID(), "Drama " + UUID.randomUUID()));
        Movie movie = movieRepository.save(new Movie(
                UUID.randomUUID(), "Screening Movie", "desc", "https://example.com/p.jpg", null, 90));
        movie.setGenres(Set.of(genre));
        Showroom showroom = showroomRepository.save(new Showroom(UUID.randomUUID(), "Screen " + UUID.randomUUID(), 20));
        ShowroomSeat seat = showroomSeatRepository.save(
                new ShowroomSeat(UUID.randomUUID(), showroom.getId(), "B", 5));
        Showtime showtime = showtimeRepository.save(new Showtime(
                UUID.randomUUID(), OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(2),
                new BigDecimal("12.50")));
        movieShowtimeRepository.save(new MovieShowtime(movie.getId(), showroom.getId(), showtime.getId()));

        assertThat(movieShowtimeRepository.existsByMovieIdAndShowroomIdAndShowtimeId(
                movie.getId(), showroom.getId(), showtime.getId()))
                .isTrue();

        Reservation reservation = reservationRepository.save(new Reservation(
                UUID.randomUUID(), UUID.randomUUID(), ReservationUserType.REGULAR, movie.getId(), showroom.getId(),
                showtime.getId(), seat.getId(), ReservationStatus.PENDING, OffsetDateTime.now().plusSeconds(30)));

        Payment payment = paymentRepository.save(new Payment(
                UUID.randomUUID(), reservation.getId(), new BigDecimal("12.50"), PaymentStatus.SUCCEEDED, null));

        assertThat(paymentRepository.findByReservationId(reservation.getId()))
                .extracting(Payment::getId)
                .contains(payment.getId());
    }
}
