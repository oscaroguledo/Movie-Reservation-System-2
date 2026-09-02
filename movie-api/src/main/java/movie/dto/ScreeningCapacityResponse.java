package movie.dto;

import movie.service.ReportingService;

public record ScreeningCapacityResponse(int totalSeats, long booked, long held, long available) {

    public static ScreeningCapacityResponse from(ReportingService.ScreeningCapacity capacity) {
        return new ScreeningCapacityResponse(
                capacity.totalSeats(), capacity.booked(), capacity.held(), capacity.available());
    }
}
