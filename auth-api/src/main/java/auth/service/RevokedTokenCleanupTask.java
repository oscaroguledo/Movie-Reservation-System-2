package auth.service;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs forever, on a fixed delay, deleting expired {@code revoked_tokens}
 * rows — the Java equivalent of the Python reference's
 * {@code purge_expired_revoked_tokens_periodically} background task.
 * Scheduling (not a single startup pass) is required by
 * {@code @EnableScheduling} on {@code Application}.
 */
@Component
public class RevokedTokenCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanupTask.class);

    private final TokenService tokenService;

    public RevokedTokenCleanupTask(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Scheduled(
            initialDelayString = "${auth.revoked-token-cleanup.interval-seconds}",
            fixedDelayString = "${auth.revoked-token-cleanup.interval-seconds}",
            timeUnit = TimeUnit.SECONDS)
    public void purgeExpired() {
        try {
            long deleted = tokenService.purgeExpired(OffsetDateTime.now());
            if (deleted > 0) {
                log.info("Purged {} expired revoked-token row(s)", deleted);
            }
        } catch (Exception e) {
            // A transient DB hiccup here shouldn't kill the scheduled task
            // permanently — it just tries again on the next tick.
            log.error("Failed to purge expired revoked tokens", e);
        }
    }
}
