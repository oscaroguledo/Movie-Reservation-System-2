package movie.dto;

import java.util.UUID;

import movie.model.ShowroomSeat;

public record ShowroomSeatResponse(UUID id, UUID showroomId, String row, Integer number) {

    public static ShowroomSeatResponse from(ShowroomSeat seat) {
        return new ShowroomSeatResponse(seat.getId(), seat.getShowroomId(), seat.getRow(), seat.getNumber());
    }
}
