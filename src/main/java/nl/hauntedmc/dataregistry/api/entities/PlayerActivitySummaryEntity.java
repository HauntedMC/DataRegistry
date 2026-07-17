package nl.hauntedmc.dataregistry.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(
        name = "player_activity_summary",
        indexes = {
                @Index(name = "idx_pas_last_seen_at", columnList = "last_seen_at"),
                @Index(name = "idx_pas_last_login_at", columnList = "last_login_at"),
                @Index(name = "idx_pas_last_logout_at", columnList = "last_logout_at")
        }
)
public class PlayerActivitySummaryEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    @Column(name = "first_seen_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant lastSeenAt;

    @Column(name = "last_login_at", columnDefinition = "TIMESTAMP")
    private Instant lastLoginAt;

    @Column(name = "last_logout_at", columnDefinition = "TIMESTAMP")
    private Instant lastLogoutAt;

    public PlayerActivitySummaryEntity() {
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
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

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getLastLogoutAt() {
        return lastLogoutAt;
    }

    public void setLastLogoutAt(Instant lastLogoutAt) {
        this.lastLogoutAt = lastLogoutAt;
    }
}
