package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(
        name = "player_playtime",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_player_playtime_player_gamemode",
                        columnNames = {"player_id", "gamemode_key"}
                )
        },
        indexes = {
                @Index(name = "idx_ppt_player", columnList = "player_id"),
                @Index(name = "idx_ppt_gamemode_time", columnList = "gamemode_key, tracked_millis"),
                @Index(name = "idx_ppt_last_tracked", columnList = "last_tracked_at"),
                @Index(name = "idx_ppt_gamemode_last_joined", columnList = "gamemode_key, last_joined_at")
        }
)
public class PlayerPlaytimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    @Column(name = "gamemode_key", length = 64, nullable = false)
    private String gamemodeKey;

    @Column(name = "tracked_millis", nullable = false)
    private long trackedMillis;

    @Column(name = "segment_count", nullable = false)
    private long segmentCount;

    @Column(name = "first_tracked_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant firstTrackedAt;

    @Column(name = "last_tracked_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant lastTrackedAt;

    @Column(name = "last_joined_at", columnDefinition = "TIMESTAMP")
    private Instant lastJoinedAt;

    @Column(name = "last_exited_at", columnDefinition = "TIMESTAMP")
    private Instant lastExitedAt;

    @Column(name = "last_logout_at", columnDefinition = "TIMESTAMP")
    private Instant lastLogoutAt;

    /* Nullable only so an additive schema upgrade can distinguish rows awaiting backfill. */
    @Column(name = "lifecycle_history_complete")
    private Boolean lifecycleHistoryComplete;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PlayerPlaytimeEntity() {
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

    public String getGamemodeKey() {
        return gamemodeKey;
    }

    public void setGamemodeKey(String gamemodeKey) {
        this.gamemodeKey = gamemodeKey;
    }

    public long getTrackedMillis() {
        return trackedMillis;
    }

    public void setTrackedMillis(long trackedMillis) {
        this.trackedMillis = trackedMillis;
    }

    public long getSegmentCount() {
        return segmentCount;
    }

    public void setSegmentCount(long segmentCount) {
        this.segmentCount = segmentCount;
    }

    public Instant getFirstTrackedAt() {
        return firstTrackedAt;
    }

    public void setFirstTrackedAt(Instant firstTrackedAt) {
        this.firstTrackedAt = firstTrackedAt;
    }

    public Instant getLastTrackedAt() {
        return lastTrackedAt;
    }

    public void setLastTrackedAt(Instant lastTrackedAt) {
        this.lastTrackedAt = lastTrackedAt;
    }

    public Instant getLastJoinedAt() {
        return lastJoinedAt;
    }

    public void setLastJoinedAt(Instant lastJoinedAt) {
        this.lastJoinedAt = lastJoinedAt;
    }

    public Instant getLastExitedAt() {
        return lastExitedAt;
    }

    public void setLastExitedAt(Instant lastExitedAt) {
        this.lastExitedAt = lastExitedAt;
    }

    public Instant getLastLogoutAt() {
        return lastLogoutAt;
    }

    public void setLastLogoutAt(Instant lastLogoutAt) {
        this.lastLogoutAt = lastLogoutAt;
    }

    public Boolean getLifecycleHistoryComplete() {
        return lifecycleHistoryComplete;
    }

    public void setLifecycleHistoryComplete(Boolean lifecycleHistoryComplete) {
        this.lifecycleHistoryComplete = lifecycleHistoryComplete;
    }
}
