package movie.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import movie.dto.MovieRequest;
import movie.dto.MovieResponse;
import movie.service.MovieService;

/** Reads are open to anyone; writes are admin-only. */
@RestController
@RequestMapping("/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MovieResponse> create(@Valid @RequestBody MovieRequest request) {
        var movie = movieService.create(
                request.title(), request.description(), request.posterImageUrl(), request.releaseDate(),
                request.durationMinutes(), request.genreIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(MovieResponse.from(movie));
    }

    @GetMapping
    public List<MovieResponse> list(@RequestParam(required = false) UUID genreId) {
        return movieService.list(genreId).stream().map(MovieResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MovieResponse get(@PathVariable UUID id) {
        return MovieResponse.from(movieService.getById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MovieResponse update(@PathVariable UUID id, @Valid @RequestBody MovieRequest request) {
        var movie = movieService.update(
                id, request.title(), request.description(), request.posterImageUrl(), request.releaseDate(),
                request.durationMinutes(), request.genreIds());
        return MovieResponse.from(movie);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        movieService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
