package auth.repository;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import auth.model.RevokedToken;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /** Rows whose own token has already expired are safe to prune — see {@code TokenService.purgeExpired}. */
    long deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
