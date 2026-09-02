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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import movie.dto.GenreRequest;
import movie.dto.GenreResponse;
import movie.service.GenreService;

/** Reads are open to anyone; writes are admin-only. */
@RestController
@RequestMapping("/genres")
public class GenreController {

    private final GenreService genreService;

    public GenreController(GenreService genreService) {
        this.genreService = genreService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenreResponse> create(@Valid @RequestBody GenreRequest request) {
        GenreResponse body = GenreResponse.from(genreService.create(request.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public List<GenreResponse> list() {
        return genreService.list().stream().map(GenreResponse::from).toList();
    }

    @GetMapping("/{id}")
    public GenreResponse get(@PathVariable UUID id) {
        return GenreResponse.from(genreService.getById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GenreResponse update(@PathVariable UUID id, @Valid @RequestBody GenreRequest request) {
        return GenreResponse.from(genreService.update(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        genreService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
