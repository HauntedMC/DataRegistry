package nl.hauntedmc.dataregistry.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "player_name_history",
        indexes = {
                @Index(name = "idx_pnh_player_seen", columnList = "player_id, last_seen_at"),
                @Index(name = "idx_pnh_username_seen", columnList = "username, last_seen_at")
        }
)
public class PlayerNameHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    @Column(name = "username", nullable = false, length = 32)
    private String username;

    @Column(name = "first_seen_at", nullable = false, columnDefinition = "TIMESTAMP", updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant lastSeenAt;

    public PlayerNameHistoryEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
