package movie.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import movie.cache.EntityCacheService;
import movie.event.GenreCreated;
import movie.event.GenreDeleted;
import movie.event.GenreUpdated;
import movie.event.MovieEventPublisher;
import movie.model.Genre;
import movie.repository.GenreRepository;
import movie.web.EntityConflictException;
import movie.web.EntityNotFoundException;

/**
 * Same cache-aside + Kafka write pipeline as auth-api's UserService:
 * Redis is written first for read-your-writes, and {@code
 * MovieEventWorker} updates Postgres asynchronously.
 */
@Service
public class GenreService {

    private static final String CACHE_PREFIX = "genre";

    private final GenreRepository genreRepository;
    private final EntityCacheService entityCacheService;
    private final MovieEventPublisher eventPublisher;

    public GenreService(
            GenreRepository genreRepository, EntityCacheService entityCacheService, MovieEventPublisher eventPublisher) {
        this.genreRepository = genreRepository;
        this.entityCacheService = entityCacheService;
        this.eventPublisher = eventPublisher;
    }

    public Genre create(String name) {
        if (genreRepository.existsByName(name)) {
            throw new EntityConflictException("Genre name already in use: " + name);
        }

        Genre genre = new Genre(UUID.randomUUID(), name);
        entityCacheService.put(CACHE_PREFIX, genre.getId(), genre);
        eventPublisher.publish(new GenreCreated(genre.getId(), genre.getName()));
        return genre;
    }

    public List<Genre> list() {
        return genreRepository.findAll();
    }

    public Genre getById(UUID id) {
        return entityCacheService
                .get(CACHE_PREFIX, id, Genre.class)
                .or(() -> genreRepository.findById(id))
                .orElseThrow(() -> new EntityNotFoundException("Genre not found: " + id));
    }

    public Genre update(UUID id, String name) {
        Genre genre = getById(id);
        genre.rename(name);

        entityCacheService.put(CACHE_PREFIX, id, genre);
        eventPublisher.publish(new GenreUpdated(id, name));
        return genre;
    }

    public void delete(UUID id) {
        getById(id);
        entityCacheService.evict(CACHE_PREFIX, id);
        eventPublisher.publish(new GenreDeleted(id));
    }
}
