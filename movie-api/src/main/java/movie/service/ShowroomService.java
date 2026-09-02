package movie.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import movie.cache.EntityCacheService;
import movie.event.MovieEventPublisher;
import movie.event.ShowroomCreated;
import movie.event.ShowroomDeleted;
import movie.event.ShowroomSeatsCreated;
import movie.event.ShowroomUpdated;
import movie.model.Showroom;
import movie.model.ShowroomSeat;
import movie.repository.ShowroomRepository;
import movie.repository.ShowroomSeatRepository;
import movie.web.EntityConflictException;
import movie.web.EntityNotFoundException;

@Service
public class ShowroomService {

    private static final String CACHE_PREFIX = "showroom";
    private static final String SEAT_CACHE_PREFIX = "showroom-seat";
    // Single-letter row labels only (A-Z) — cinemas rarely need more, and
    // it keeps bulk seat creation simple rather than spreadsheet-style
    // multi-letter labels (AA, AB, ...) for an edge case unlikely to matter.
    private static final int MAX_ROWS = 26;

    private final ShowroomRepository showroomRepository;
    private final ShowroomSeatRepository showroomSeatRepository;
    private final EntityCacheService entityCacheService;
    private final MovieEventPublisher eventPublisher;

    public ShowroomService(
            ShowroomRepository showroomRepository,
            ShowroomSeatRepository showroomSeatRepository,
            EntityCacheService entityCacheService,
            MovieEventPublisher eventPublisher) {
        this.showroomRepository = showroomRepository;
        this.showroomSeatRepository = showroomSeatRepository;
        this.entityCacheService = entityCacheService;
        this.eventPublisher = eventPublisher;
    }

    public Showroom create(String name, Integer capacity) {
        if (showroomRepository.existsByName(name)) {
            throw new EntityConflictException("Showroom name already in use: " + name);
        }

        Showroom showroom = new Showroom(UUID.randomUUID(), name, capacity);
        entityCacheService.put(CACHE_PREFIX, showroom.getId(), showroom);
        eventPublisher.publish(new ShowroomCreated(showroom.getId(), name, capacity));
        return showroom;
    }

    public Showroom getById(UUID id) {
        return entityCacheService
                .get(CACHE_PREFIX, id, Showroom.class)
                .or(() -> showroomRepository.findById(id))
                .orElseThrow(() -> new EntityNotFoundException("Showroom not found: " + id));
    }

    public List<Showroom> list() {
        return showroomRepository.findAll();
    }

    public Showroom update(UUID id, String name, Integer capacity) {
        Showroom showroom = getById(id);
        showroom.applyUpdate(name, capacity);

        entityCacheService.put(CACHE_PREFIX, id, showroom);
        eventPublisher.publish(new ShowroomUpdated(id, name, capacity));
        return showroom;
    }

    public void delete(UUID id) {
        getById(id);
        entityCacheService.evict(CACHE_PREFIX, id);
        eventPublisher.publish(new ShowroomDeleted(id));
    }

    /** Generates a rows x seatsPerRow grid (row "A".."Z"), skipping any label that already exists. */
    public List<ShowroomSeat> bulkCreateSeats(UUID showroomId, int rows, int seatsPerRow) {
        getById(showroomId);

        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("rows must be between 1 and " + MAX_ROWS);
        }
        if (seatsPerRow < 1) {
            throw new IllegalArgumentException("seatsPerRow must be at least 1");
        }

        List<ShowroomSeat> created = new ArrayList<>();
        List<ShowroomSeatsCreated.SeatData> seatData = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            for (int n = 1; n <= seatsPerRow; n++) {
                if (showroomSeatRepository.existsByShowroomIdAndRowAndNumber(showroomId, rowLabel, n)) {
                    continue;
                }
                UUID seatId = UUID.randomUUID();
                ShowroomSeat seat = new ShowroomSeat(seatId, showroomId, rowLabel, n);
                entityCacheService.put(SEAT_CACHE_PREFIX, seatId, seat);
                created.add(seat);
                seatData.add(new ShowroomSeatsCreated.SeatData(seatId, rowLabel, n));
            }
        }

        if (!seatData.isEmpty()) {
            eventPublisher.publish(new ShowroomSeatsCreated(showroomId, seatData));
        }
        return created;
    }

    public List<ShowroomSeat> listSeats(UUID showroomId) {
        return showroomSeatRepository.findByShowroomId(showroomId);
    }
}
