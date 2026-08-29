package auth.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users", schema = "auth_api")
public class User {

    // Application-assigned, not @GeneratedValue: the write pipeline caches
    // a fully-formed User in Redis and returns its id to the caller before
    // the Kafka worker ever persists it to Postgres, so the id has to
    // exist before Postgres is involved. (The migration's DEFAULT
    // gen_random_uuid() stays only as a safety net for direct SQL.)
    @Id
    private UUID id;

    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType = UserType.REGULAR;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
        // for JPA
    }

    public User(UUID id, String email, String firstName, String lastName, String passwordHash, UserType userType) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.userType = userType;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Mutates the editable fields and stamps {@code updatedAt} directly.
     * Used by {@code UserService} to update a copy that's about to be
     * cached in Redis and returned to the caller — this instance is a
     * plain value object at that point (round-tripped through Redis/JSON
     * or freshly read), not a Hibernate-managed entity, so {@link
     * #onUpdate()} won't fire for it; the actual Postgres row gets its
     * updatedAt from that callback once {@code AuthEventWorker} persists
     * it.
     */
    public void applyUpdate(String firstName, String lastName, String passwordHash) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
