package auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class RevokedTokenCleanupTaskTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final RevokedTokenCleanupTask task = new RevokedTokenCleanupTask(tokenService);

    @Test
    void delegatesToTokenServicePurgeExpired() {
        when(tokenService.purgeExpired(any())).thenReturn(2L);

        task.purgeExpired();

        verify(tokenService).purgeExpired(any(OffsetDateTime.class));
    }

    @Test
    void swallowsAFailureSoTheScheduledTaskKeepsRunningOnTheNextTick() {
        when(tokenService.purgeExpired(any())).thenThrow(new RuntimeException("db unreachable"));

        // Must not propagate: Spring's @Scheduled stops rescheduling a
        // fixed-delay task entirely if an invocation throws.
        task.purgeExpired();
    }
}
