package movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import movie.cache.EntityCacheService;
import movie.event.GenreCreated;
import movie.event.GenreDeleted;
import movie.event.GenreUpdated;
import movie.event.MovieEventPublisher;
import movie.model.Genre;
import movie.repository.GenreRepository;
import movie.web.EntityConflictException;
import movie.web.EntityNotFoundException;

class GenreServiceTest {

    private final GenreRepository genreRepository = mock(GenreRepository.class);
    private final EntityCacheService entityCacheService = mock(EntityCacheService.class);
    private final MovieEventPublisher eventPublisher = mock(MovieEventPublisher.class);
    private final GenreService genreService = new GenreService(genreRepository, entityCacheService, eventPublisher);

    @Test
    void createCachesInRedisAndPublishesAnEventInsteadOfWritingPostgresDirectly() {
        when(genreRepository.existsByName("Action")).thenReturn(false);

        Genre genre = genreService.create("Action");

        assertThat(genre.getName()).isEqualTo("Action");
        verify(entityCacheService).put("genre", genre.getId(), genre);
        verify(eventPublisher).publish(any(GenreCreated.class));
        verify(genreRepository, never()).save(any());
    }

    @Test
    void createRejectsADuplicateName() {
        when(genreRepository.existsByName("Action")).thenReturn(true);

        assertThatThrownBy(() -> genreService.create("Action")).isInstanceOf(EntityConflictException.class);
    }

    @Test
    void getByIdFallsBackToPostgresOnACacheMiss() {
        UUID id = UUID.randomUUID();
        Genre genre = new Genre(id, "Drama");
        when(entityCacheService.get("genre", id, Genre.class)).thenReturn(Optional.empty());
        when(genreRepository.findById(id)).thenReturn(Optional.of(genre));

        assertThat(genreService.getById(id)).isEqualTo(genre);
    }

    @Test
    void getByIdThrowsWhenNotFoundAnywhere() {
        UUID id = UUID.randomUUID();
        when(entityCacheService.get("genre", id, Genre.class)).thenReturn(Optional.empty());
        when(genreRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> genreService.getById(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateRenamesAndPublishesAnEvent() {
        UUID id = UUID.randomUUID();
        Genre genre = new Genre(id, "Old Name");
        when(entityCacheService.get("genre", id, Genre.class)).thenReturn(Optional.of(genre));

        Genre updated = genreService.update(id, "New Name");

        assertThat(updated.getName()).isEqualTo("New Name");
        verify(entityCacheService).put("genre", id, genre);
        verify(eventPublisher).publish(any(GenreUpdated.class));
    }

    @Test
    void deleteEvictsFromCacheAndPublishesAnEvent() {
        UUID id = UUID.randomUUID();
        Genre genre = new Genre(id, "To Delete");
        when(entityCacheService.get("genre", id, Genre.class)).thenReturn(Optional.of(genre));

        genreService.delete(id);

        verify(entityCacheService).evict("genre", id);
        verify(eventPublisher).publish(any(GenreDeleted.class));
    }
}
