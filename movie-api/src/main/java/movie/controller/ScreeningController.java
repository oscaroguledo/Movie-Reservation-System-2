package movie.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import movie.dto.ScreeningRequest;
import movie.dto.ScreeningResponse;
import movie.dto.SeatStatusResponse;
import movie.service.ScreeningService;
import movie.service.ScreeningView;
import movie.web.EntityConflictException;

/** Scheduling/deleting a screening is admin-only; browsing and the seat map are open to anyone. */
@RestController
public class ScreeningController {

    private final ScreeningService screeningService;

    public ScreeningController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @PostMapping("/screenings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScreeningResponse> schedule(@Valid @RequestBody ScreeningRequest request) {
        ScreeningView view = screeningService.schedule(
                request.movieId(), request.showroomId(), request.startTime(), request.endTime(), request.price());
        return ResponseEntity.status(HttpStatus.CREATED).body(ScreeningResponse.from(view));
    }

    @GetMapping("/screenings")
    public List<ScreeningResponse> list(
            @RequestParam(required = false) LocalDate showDate,
            @RequestParam(required = false) UUID movieId,
            @RequestParam(required = false) UUID showroomId) {
        List<ScreeningView> views;
        if (showDate != null) {
            views = screeningService.listForDate(showDate);
        } else if (movieId != null || showroomId != null) {
            views = screeningService.listUpcoming(movieId, showroomId);
        } else {
            throw new EntityConflictException("Provide showDate, movieId, or showroomId");
        }
        return views.stream().map(ScreeningResponse::from).toList();
    }

    @DeleteMapping("/screenings/{movieId}/{showroomId}/{showtimeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID movieId, @PathVariable UUID showroomId, @PathVariable UUID showtimeId) {
        screeningService.delete(movieId, showroomId, showtimeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/screenings/{movieId}/{showroomId}/{showtimeId}/seats")
    public List<SeatStatusResponse> seatMap(
            @PathVariable UUID movieId, @PathVariable UUID showroomId, @PathVariable UUID showtimeId) {
        return screeningService.seatMap(movieId, showroomId, showtimeId).stream()
                .map(SeatStatusResponse::from)
                .toList();
    }
}
