package movie.cache;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import movie.model.Showtime;

/**
 * Redis operations backing screening scheduling: a short-lived lock
 * guards a check-then-append against the showroom's own schedule (the
 * overlap-prevention mechanism — see ScreeningService), plus cache-aside
 * for Showtime and a screening-existence marker.
 *
 * <p>Deliberately simplified from the Python reference in one place:
 * this doesn't maintain the reference's separate Redis date-index cache
 * for {@code list_for_date}/{@code list_upcoming} — those read Postgres
 * directly instead. That index is a performance optimization, not part
 * of the correctness-critical behavior (overlap prevention, seat
 * locking) this port is faithful to.
 */
@Component
public class ScreeningCacheService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private final RedisTemplate<String, Object> redisTemplate;
    private final EntityCacheService entityCacheService;

    public ScreeningCacheService(RedisTemplate<String, Object> redisTemplate, EntityCacheService entityCacheService) {
        this.redisTemplate = redisTemplate;
        this.entityCacheService = entityCacheService;
    }

    public boolean lockSchedule(UUID showroomId) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(showroomId), token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlockSchedule(UUID showroomId) {
        redisTemplate.delete(lockKey(showroomId));
    }

    @SuppressWarnings("unchecked")
    public List<ScheduleInterval> getSchedule(UUID showroomId) {
        Object value = redisTemplate.opsForValue().get(scheduleKey(showroomId));
        return value instanceof List<?> list ? new ArrayList<>((List<ScheduleInterval>) list) : new ArrayList<>();
    }

    public void addToSchedule(UUID showroomId, UUID showtimeId, OffsetDateTime start, OffsetDateTime end) {
        List<ScheduleInterval> schedule = getSchedule(showroomId);
        schedule.add(new ScheduleInterval(showtimeId, start, end));
        redisTemplate.opsForValue().set(scheduleKey(showroomId), schedule);
    }

    public void removeFromSchedule(UUID showroomId, UUID showtimeId) {
        List<ScheduleInterval> schedule = getSchedule(showroomId);
        schedule.removeIf(interval -> interval.showtimeId().equals(showtimeId));
        redisTemplate.opsForValue().set(scheduleKey(showroomId), schedule);
    }

    public void saveShowtime(Showtime showtime) {
        entityCacheService.put("showtime", showtime.getId(), showtime);
    }

    public Optional<Showtime> getShowtime(UUID showtimeId) {
        return entityCacheService.get("showtime", showtimeId, Showtime.class);
    }

    public void markScreening(UUID movieId, UUID showroomId, UUID showtimeId) {
        redisTemplate.opsForValue().set(screeningKey(movieId, showroomId, showtimeId), Boolean.TRUE);
    }

    public void unmarkScreening(UUID movieId, UUID showroomId, UUID showtimeId) {
        redisTemplate.delete(screeningKey(movieId, showroomId, showtimeId));
    }

    public boolean screeningExists(UUID movieId, UUID showroomId, UUID showtimeId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().get(screeningKey(movieId, showroomId, showtimeId)));
    }

    private static String lockKey(UUID showroomId) {
        return "movie:schedule-lock:" + showroomId;
    }

    private static String scheduleKey(UUID showroomId) {
        return "movie:schedule:" + showroomId;
    }

    private static String screeningKey(UUID movieId, UUID showroomId, UUID showtimeId) {
        return "movie:screening:" + movieId + ":" + showroomId + ":" + showtimeId;
    }

    public record ScheduleInterval(UUID showtimeId, OffsetDateTime start, OffsetDateTime end) {
    }
}
