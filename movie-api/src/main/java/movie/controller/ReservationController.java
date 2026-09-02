package movie.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import movie.dto.PaymentCreateRequest;
import movie.dto.ReservationCreateRequest;
import movie.dto.ReservationResponse;
import movie.model.Reservation;
import movie.security.MoviePrincipal;
import movie.service.ReservationService;
import movie.web.EntityNotFoundException;
import movie.web.NotAuthenticatedException;

/** Open to guests for creating/viewing/confirming/cancelling a hold; "my reservations" needs a real identity. */
@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<List<ReservationResponse>> create(@Valid @RequestBody ReservationCreateRequest request) {
        List<Reservation> reservations = reservationService.createHold(
                currentPrincipal(), request.movieId(), request.showroomId(), request.showtimeId(),
                request.showroomSeatIds());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservations.stream().map(ReservationResponse::from).toList());
    }

    @PostMapping("/{id}/confirm")
    public ReservationResponse confirm(@PathVariable UUID id, @Valid @RequestBody PaymentCreateRequest request) {
        Reservation reservation =
                reservationService.confirm(currentPrincipal(), id, request.amount(), request.providerReference());
        return ReservationResponse.from(requireFound(reservation, id));
    }

    @GetMapping
    public List<ReservationResponse> listMine() {
        MoviePrincipal principal = currentPrincipal();
        if (principal.isGuest()) {
            throw new NotAuthenticatedException("Not authenticated");
        }
        return reservationService.listForPrincipal(principal).stream().map(ReservationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable UUID id) {
        Reservation reservation = reservationService.getForPrincipal(currentPrincipal(), id);
        return ReservationResponse.from(requireFound(reservation, id));
    }

    @PatchMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable UUID id) {
        Reservation reservation = reservationService.cancel(currentPrincipal(), id);
        return ReservationResponse.from(requireFound(reservation, id));
    }

    private static Reservation requireFound(Reservation reservation, UUID id) {
        if (reservation == null) {
            throw new EntityNotFoundException("Reservation not found: " + id);
        }
        return reservation;
    }

    private static MoviePrincipal currentPrincipal() {
        return (MoviePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
