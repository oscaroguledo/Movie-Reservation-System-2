package movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import movie.cache.EntityCacheService;
import movie.event.MovieEventPublisher;
import movie.event.ShowroomSeatsCreated;
import movie.model.Showroom;
import movie.repository.ShowroomRepository;
import movie.repository.ShowroomSeatRepository;
import movie.web.EntityConflictException;
import movie.web.EntityNotFoundException;

class ShowroomServiceTest {

    private final ShowroomRepository showroomRepository = mock(ShowroomRepository.class);
    private final ShowroomSeatRepository showroomSeatRepository = mock(ShowroomSeatRepository.class);
    private final EntityCacheService entityCacheService = mock(EntityCacheService.class);
    private final MovieEventPublisher eventPublisher = mock(MovieEventPublisher.class);
    private final ShowroomService showroomService = new ShowroomService(
            showroomRepository, showroomSeatRepository, entityCacheService, eventPublisher);

    @Test
    void createRejectsADuplicateName() {
        when(showroomRepository.existsByName("Room 1")).thenReturn(true);

        assertThatThrownBy(() -> showroomService.create("Room 1", 50))
                .isInstanceOf(EntityConflictException.class);
    }

    @Test
    void getByIdFallsBackToPostgresOnACacheMiss() {
        UUID id = UUID.randomUUID();
        Showroom showroom = new Showroom(id, "Room 1", 50);
        when(entityCacheService.get("showroom", id, Showroom.class)).thenReturn(Optional.empty());
        when(showroomRepository.findById(id)).thenReturn(Optional.of(showroom));

        assertThat(showroomService.getById(id)).isEqualTo(showroom);
    }

    @Test
    void getByIdThrowsWhenNotFoundAnywhere() {
        UUID id = UUID.randomUUID();
        when(entityCacheService.get("showroom", id, Showroom.class)).thenReturn(Optional.empty());
        when(showroomRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> showroomService.getById(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void bulkCreateSeatsGeneratesARowsBySeatsPerRowGridSkippingExisting() {
        UUID showroomId = UUID.randomUUID();
        Showroom showroom = new Showroom(showroomId, "Room 1", 50);
        when(entityCacheService.get("showroom", showroomId, Showroom.class)).thenReturn(Optional.of(showroom));
        when(showroomSeatRepository.existsByShowroomIdAndRowAndNumber(showroomId, "A", 2)).thenReturn(true);

        var seats = showroomService.bulkCreateSeats(showroomId, 2, 2);

        assertThat(seats).hasSize(3); // A1, B1, B2 — A2 skipped as already existing
        verify(eventPublisher).publish(any(ShowroomSeatsCreated.class));
    }

    @Test
    void bulkCreateSeatsRejectsTooManyRows() {
        UUID showroomId = UUID.randomUUID();
        when(entityCacheService.get("showroom", showroomId, Showroom.class))
                .thenReturn(Optional.of(new Showroom(showroomId, "Room 1", 50)));

        assertThatThrownBy(() -> showroomService.bulkCreateSeats(showroomId, 27, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bulkCreateSeatsPublishesNothingWhenEverySeatAlreadyExists() {
        UUID showroomId = UUID.randomUUID();
        when(entityCacheService.get("showroom", showroomId, Showroom.class))
                .thenReturn(Optional.of(new Showroom(showroomId, "Room 1", 50)));
        when(showroomSeatRepository.existsByShowroomIdAndRowAndNumber(showroomId, "A", 1)).thenReturn(true);

        var seats = showroomService.bulkCreateSeats(showroomId, 1, 1);

        assertThat(seats).isEmpty();
        verify(eventPublisher, never()).publish(any());
    }
}
