package movie.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A seat label ("row" + "number") is unique within its showroom, reusable across showrooms. */
@Entity
@Table(name = "showroom_seats", schema = "movie_api")
public class ShowroomSeat {

    @Id
    private UUID id;

    @Column(name = "showroom_id", nullable = false)
    private UUID showroomId;

    @Column(name = "row", nullable = false, length = 5)
    private String row;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ShowroomSeat() {
        // for JPA
    }

    public ShowroomSeat(UUID id, UUID showroomId, String row, Integer number) {
        this.id = id;
        this.showroomId = showroomId;
        this.row = row;
        this.number = number;
        this.createdAt = OffsetDateTime.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getShowroomId() {
        return showroomId;
    }

    public String getRow() {
        return row;
    }

    public Integer getNumber() {
        return number;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
