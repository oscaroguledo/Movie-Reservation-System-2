package auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import auth.IntegrationTestSupport;

/**
 * Exercises the sliding-window algorithm directly against real Redis,
 * with its own small limit/window per call — independent of whatever
 * app-wide default is configured, and of any other test's Redis traffic.
 */
@SpringBootTest
class RateLimiterTest extends IntegrationTestSupport {

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void allowsUpToTheLimitThenRejects() {
        String key = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofSeconds(60))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofSeconds(60))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofSeconds(60))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofSeconds(60))).isFalse();
    }

    @Test
    void differentKeysHaveIndependentLimits() {
        String keyA = UUID.randomUUID().toString();
        String keyB = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryAcquire(keyA, 1, Duration.ofSeconds(60))).isTrue();
        assertThat(rateLimiter.tryAcquire(keyA, 1, Duration.ofSeconds(60))).isFalse();
        assertThat(rateLimiter.tryAcquire(keyB, 1, Duration.ofSeconds(60))).isTrue();
    }

    @Test
    void slidesOpenAgainOnceOldEntriesAgeOutOfTheWindow() throws InterruptedException {
        String key = UUID.randomUUID().toString();
        Duration window = Duration.ofSeconds(1);

        assertThat(rateLimiter.tryAcquire(key, 1, window)).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 1, window)).isFalse();

        Thread.sleep(1100);

        assertThat(rateLimiter.tryAcquire(key, 1, window)).isTrue();
    }
}
