package movie.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import movie.cache.EntityCacheService;
import movie.event.MovieCreated;
import movie.event.MovieDeleted;
import movie.event.MovieEventPublisher;
import movie.event.MovieUpdated;
import movie.model.Genre;
import movie.model.Movie;
import movie.repository.GenreRepository;
import movie.repository.MovieRepository;
import movie.web.EntityNotFoundException;

/** Same cache-aside + Kafka write pipeline as GenreService. */
@Service
public class MovieService {

    private static final String CACHE_PREFIX = "movie";

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final EntityCacheService entityCacheService;
    private final MovieEventPublisher eventPublisher;

    public MovieService(
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            EntityCacheService entityCacheService,
            MovieEventPublisher eventPublisher) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.entityCacheService = entityCacheService;
        this.eventPublisher = eventPublisher;
    }

    public Movie create(
            String title,
            String description,
            String posterImageUrl,
            LocalDate releaseDate,
            Integer durationMinutes,
            List<UUID> genreIds) {
        Movie movie = new Movie(UUID.randomUUID(), title, description, posterImageUrl, releaseDate, durationMinutes);
        movie.setGenres(resolveGenres(genreIds));

        entityCacheService.put(CACHE_PREFIX, movie.getId(), movie);
        eventPublisher.publish(new MovieCreated(
                movie.getId(), title, description, posterImageUrl, releaseDate, durationMinutes, genreIds));
        return movie;
    }

    public Movie getById(UUID id) {
        return entityCacheService
                .get(CACHE_PREFIX, id, Movie.class)
                .or(() -> movieRepository.findById(id))
                .orElseThrow(() -> new EntityNotFoundException("Movie not found: " + id));
    }

    /** Direct Postgres read (not cache-aside) — a browsing/reporting-style read, like auth-api's user listing. */
    public List<Movie> list(UUID genreId) {
        return genreId != null ? movieRepository.findByGenres_Id(genreId) : movieRepository.findAll();
    }

    public Movie update(
            UUID id,
            String title,
            String description,
            String posterImageUrl,
            LocalDate releaseDate,
            Integer durationMinutes,
            List<UUID> genreIds) {
        Movie movie = getById(id);
        movie.applyUpdate(title, description, posterImageUrl, releaseDate, durationMinutes);
        movie.setGenres(resolveGenres(genreIds));

        entityCacheService.put(CACHE_PREFIX, id, movie);
        eventPublisher.publish(
                new MovieUpdated(id, title, description, posterImageUrl, releaseDate, durationMinutes, genreIds));
        return movie;
    }

    public void delete(UUID id) {
        getById(id);
        entityCacheService.evict(CACHE_PREFIX, id);
        eventPublisher.publish(new MovieDeleted(id));
    }

    private Set<Genre> resolveGenres(List<UUID> genreIds) {
        return genreIds == null || genreIds.isEmpty() ? Set.of() : Set.copyOf(genreRepository.findAllById(genreIds));
    }
}
