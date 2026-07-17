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
                @Index(name = "idx_ppt_last_tracked", columnList = "last_tracked_at")
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
}
