package movie.dto;

import java.util.UUID;

import movie.service.ScreeningView;

public record ScreeningResponse(MovieResponse movie, ShowtimeResponse showtime, UUID showroomId) {

    public static ScreeningResponse from(ScreeningView view) {
        return new ScreeningResponse(
                MovieResponse.from(view.movie()), ShowtimeResponse.from(view.showtime()), view.showroomId());
    }
}
