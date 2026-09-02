package auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import auth.IntegrationTestSupport;
import auth.model.RevokedToken;
import auth.repository.RevokedTokenRepository;

/**
 * Exercises {@link TokenService#purgeExpired} — and the derived
 * {@code deleteByExpiresAtBefore} query it delegates to — against real
 * Postgres, since a derived delete query is exactly the kind of thing
 * that can silently do the wrong thing (wrong comparison direction,
 * wrong column) without a real backing database to catch it.
 */
@SpringBootTest
class TokenServicePurgeIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    void deletesOnlyRowsPastTheirOwnExpiryLeavingUnexpiredOnesAlone() {
        OffsetDateTime now = OffsetDateTime.now();
        String expiredJti = "expired-" + now.toInstant().toEpochMilli();
        String liveJti = "live-" + now.toInstant().toEpochMilli();
        revokedTokenRepository.save(new RevokedToken(expiredJti, now.minusMinutes(5)));
        revokedTokenRepository.save(new RevokedToken(liveJti, now.plusMinutes(30)));

        long deleted = tokenService.purgeExpired(now);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(revokedTokenRepository.existsById(expiredJti)).isFalse();
        assertThat(revokedTokenRepository.existsById(liveJti)).isTrue();
    }
}
