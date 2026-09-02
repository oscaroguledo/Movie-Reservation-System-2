package movie.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import movie.dto.PaymentResponse;
import movie.model.Reservation;
import movie.security.MoviePrincipal;
import movie.service.PaymentService;
import movie.service.ReservationService;
import movie.web.EntityNotFoundException;
import movie.web.NotAuthorizedException;

@RestController
public class PaymentController {

    private final ReservationService reservationService;
    private final PaymentService paymentService;

    public PaymentController(ReservationService reservationService, PaymentService paymentService) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
    }

    @GetMapping("/reservations/{reservationId}/payments")
    public List<PaymentResponse> listForReservation(@PathVariable UUID reservationId) {
        Reservation reservation = reservationService.get(reservationId);
        if (reservation == null) {
            throw new EntityNotFoundException("Reservation not found: " + reservationId);
        }

        var principal = (MoviePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!ReservationService.canAccess(principal, reservation)) {
            throw new NotAuthorizedException("Not authorized to view these payments");
        }

        return paymentService.listForReservation(reservationId).stream().map(PaymentResponse::from).toList();
    }
}
