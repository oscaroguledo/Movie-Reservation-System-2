package movie.service;

import java.util.UUID;

import movie.model.Movie;
import movie.model.Showtime;

public record ScreeningView(Movie movie, Showtime showtime, UUID showroomId) {
}
