package movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import movie.cache.EntityCacheService;
import movie.event.MovieCreated;
import movie.event.MovieEventPublisher;
import movie.model.Genre;
import movie.model.Movie;
import movie.repository.GenreRepository;
import movie.repository.MovieRepository;
import movie.web.EntityNotFoundException;

class MovieServiceTest {

    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final GenreRepository genreRepository = mock(GenreRepository.class);
    private final EntityCacheService entityCacheService = mock(EntityCacheService.class);
    private final MovieEventPublisher eventPublisher = mock(MovieEventPublisher.class);
    private final MovieService movieService =
            new MovieService(movieRepository, genreRepository, entityCacheService, eventPublisher);

    @Test
    void createResolvesGenresCachesAndPublishesAnEvent() {
        UUID genreId = UUID.randomUUID();
        Genre genre = new Genre(genreId, "Action");
        when(genreRepository.findAllById(List.of(genreId))).thenReturn(List.of(genre));

        Movie movie = movieService.create(
                "Title", "Description", "https://example.com/p.jpg", null, 120, List.of(genreId));

        assertThat(movie.getGenres()).containsExactly(genre);
        verify(entityCacheService).put(eq("movie"), eq(movie.getId()), eq(movie));
        verify(eventPublisher).publish(any(MovieCreated.class));
        verify(movieRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createWithNoGenresIsAllowed() {
        Movie movie = movieService.create("Title", "Description", "https://example.com/p.jpg", null, null, null);

        assertThat(movie.getGenres()).isEmpty();
    }

    @Test
    void getByIdFallsBackToPostgresOnACacheMiss() {
        UUID id = UUID.randomUUID();
        Movie movie = new Movie(id, "T", "D", "https://example.com/p.jpg", null, null);
        when(entityCacheService.get("movie", id, Movie.class)).thenReturn(Optional.empty());
        when(movieRepository.findById(id)).thenReturn(Optional.of(movie));

        assertThat(movieService.getById(id)).isEqualTo(movie);
    }

    @Test
    void getByIdThrowsWhenNotFoundAnywhere() {
        UUID id = UUID.randomUUID();
        when(entityCacheService.get("movie", id, Movie.class)).thenReturn(Optional.empty());
        when(movieRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.getById(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listFiltersByGenreWhenProvided() {
        UUID genreId = UUID.randomUUID();
        Movie movie = new Movie(UUID.randomUUID(), "T", "D", "https://example.com/p.jpg", null, null);
        when(movieRepository.findByGenres_Id(genreId)).thenReturn(List.of(movie));

        assertThat(movieService.list(genreId)).containsExactly(movie);
    }

    @Test
    void listReturnsAllWhenNoGenreFilter() {
        Movie movie = new Movie(UUID.randomUUID(), "T", "D", "https://example.com/p.jpg", null, null);
        when(movieRepository.findAll()).thenReturn(List.of(movie));

        assertThat(movieService.list(null)).containsExactly(movie);
    }
}
