package movie.cache;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

/**
 * The actual overbooking guard for reservations (see the reservation
 * service, next step) — a seat lock is a {@code SET key value NX PX}'d
 * key with a TTL matching the hold window; Postgres's own unique-index
 * backstop (see the migration) is secondary, not the live guard. Also
 * backs {@code ScreeningService}'s seat map and screening-deletion
 * guard, which is why this lands here rather than with the reservation
 * logic itself.
 */
@Component
public class SeatLockService {

    private static final String SEAT_LOCK_PREFIX = "movie:seat-lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    public SeatLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** SETNX with a TTL matching the hold window — the fast-path overbooking guard. */
    public boolean acquireSeat(UUID showtimeId, UUID seatId, UUID reservationId, Duration holdTtl) {
        Boolean acquired = redisTemplate
                .opsForValue()
                .setIfAbsent(seatLockKey(showtimeId, seatId), reservationId.toString(), holdTtl);
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseSeat(UUID showtimeId, UUID seatId) {
        redisTemplate.delete(seatLockKey(showtimeId, seatId));
    }

    /** Removes the TTL once a hold is confirmed — it's durably booked now, must not expire. */
    public void persistSeat(UUID showtimeId, UUID seatId) {
        redisTemplate.persist(seatLockKey(showtimeId, seatId));
    }

    public String getSeatHolder(UUID showtimeId, UUID seatId) {
        Object value = redisTemplate.opsForValue().get(seatLockKey(showtimeId, seatId));
        return value != null ? value.toString() : null;
    }

    /** Whether any seat for this screening has a hold or booking — used to refuse unscheduling it. */
    public boolean hasAnyActiveSeat(UUID showtimeId) {
        ScanOptions options =
                ScanOptions.scanOptions().match(SEAT_LOCK_PREFIX + showtimeId + ":*").count(100).build();
        try (var cursor = redisTemplate.scan(options)) {
            return cursor.hasNext();
        }
    }

    /**
     * Permanent marker set the moment a hold is created and never
     * cleared — unlike the seat lock, survives cancel/expire so a
     * screening delete can refuse it without waiting on the Kafka
     * worker to durably persist the reservation to Postgres first.
     */
    public void markReservationHistory(UUID showtimeId) {
        redisTemplate.opsForValue().set(historyKey(showtimeId), Boolean.TRUE);
    }

    public boolean hasReservationHistory(UUID showtimeId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().get(historyKey(showtimeId)));
    }

    private static String seatLockKey(UUID showtimeId, UUID seatId) {
        return SEAT_LOCK_PREFIX + showtimeId + ":" + seatId;
    }

    private static String historyKey(UUID showtimeId) {
        return "movie:reservation-history:" + showtimeId;
    }
}
