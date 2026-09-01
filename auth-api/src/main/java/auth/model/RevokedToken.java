package auth.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A revoked JWT, keyed by its {@code jti} claim, so a token can be checked
 * for server-side revocation before {@code expires_at} passes. Rows older
 * than their own {@code expires_at} are safe to prune.
 */
@Entity
@Table(name = "revoked_tokens", schema = "auth_api")
public class RevokedToken {

    @Id
    @Column(name = "jti", nullable = false, updatable = false)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private OffsetDateTime revokedAt;

    protected RevokedToken() {
        // for JPA
    }

    public RevokedToken(String jti, OffsetDateTime expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.revokedAt = OffsetDateTime.now();
    }

    public String getJti() {
        return jti;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }
}
