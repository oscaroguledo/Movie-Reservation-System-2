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
import movie.dto.ShowroomRequest;
import movie.dto.ShowroomResponse;
import movie.dto.ShowroomSeatBulkCreateRequest;
import movie.dto.ShowroomSeatResponse;
import movie.service.ShowroomService;

/** Reads are open to anyone; writes are admin-only. */
@RestController
@RequestMapping("/showrooms")
public class ShowroomController {

    private final ShowroomService showroomService;

    public ShowroomController(ShowroomService showroomService) {
        this.showroomService = showroomService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShowroomResponse> create(@Valid @RequestBody ShowroomRequest request) {
        var showroom = showroomService.create(request.name(), request.capacity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShowroomResponse.from(showroom));
    }

    @GetMapping
    public List<ShowroomResponse> list() {
        return showroomService.list().stream().map(ShowroomResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ShowroomResponse get(@PathVariable UUID id) {
        return ShowroomResponse.from(showroomService.getById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ShowroomResponse update(@PathVariable UUID id, @Valid @RequestBody ShowroomRequest request) {
        return ShowroomResponse.from(showroomService.update(id, request.name(), request.capacity()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        showroomService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/seats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ShowroomSeatResponse>> createSeats(
            @PathVariable UUID id, @Valid @RequestBody ShowroomSeatBulkCreateRequest request) {
        var seats = showroomService.bulkCreateSeats(id, request.rows(), request.seatsPerRow()).stream()
                .map(ShowroomSeatResponse::from)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(seats);
    }

    @GetMapping("/{id}/seats")
    public List<ShowroomSeatResponse> listSeats(@PathVariable UUID id) {
        return showroomService.listSeats(id).stream().map(ShowroomSeatResponse::from).toList();
    }
}
