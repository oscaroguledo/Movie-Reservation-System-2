package movie.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import movie.model.Showroom;

public record ShowroomResponse(UUID id, String name, Integer capacity, OffsetDateTime createdAt) {

    public static ShowroomResponse from(Showroom showroom) {
        return new ShowroomResponse(showroom.getId(), showroom.getName(), showroom.getCapacity(), showroom.getCreatedAt());
    }
}
