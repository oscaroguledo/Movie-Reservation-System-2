package movie.dto;

import movie.service.SeatStatus;

public record SeatStatusResponse(ShowroomSeatResponse seat, String status) {

    public static SeatStatusResponse from(SeatStatus seatStatus) {
        return new SeatStatusResponse(ShowroomSeatResponse.from(seatStatus.seat()), seatStatus.status());
    }
}
