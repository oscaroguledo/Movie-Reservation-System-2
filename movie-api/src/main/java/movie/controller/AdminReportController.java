package movie.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import movie.dto.ReservationResponse;
import movie.dto.RevenueResponse;
import movie.dto.ScreeningCapacityResponse;
import movie.model.ReservationStatus;
import movie.service.ReportingService;

/** Every endpoint here is admin-only. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportingService reportingService;

    public AdminReportController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/admin/reservations")
    public List<ReservationResponse> allReservations(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        ReservationStatus parsed = status != null ? ReservationStatus.fromValue(status) : null;
        return reportingService.allReservations(parsed, limit, offset).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @GetMapping("/admin/screenings/{movieId}/{showroomId}/{showtimeId}/capacity")
    public ScreeningCapacityResponse screeningCapacity(
            @PathVariable UUID movieId, @PathVariable UUID showroomId, @PathVariable UUID showtimeId) {
        return ScreeningCapacityResponse.from(reportingService.screeningCapacity(movieId, showroomId, showtimeId));
    }

    @GetMapping("/admin/revenue")
    public RevenueResponse revenue() {
        return RevenueResponse.from(reportingService.revenue());
    }
}
