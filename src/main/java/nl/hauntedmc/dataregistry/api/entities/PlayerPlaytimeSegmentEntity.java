package nl.hauntedmc.dataregistry.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(
        name = "player_playtime_segments",
        indexes = {
                @Index(name = "idx_ppts_player_open", columnList = "player_id, ended_at"),
                @Index(name = "idx_ppts_player_started", columnList = "player_id, started_at"),
                @Index(name = "idx_ppts_gamemode_started", columnList = "gamemode_key, started_at"),
                @Index(name = "idx_ppts_session_open", columnList = "session_id, ended_at"),
                @Index(name = "idx_ppts_open_started", columnList = "ended_at, started_at")
        }
)
public class PlayerPlaytimeSegmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private PlayerSessionEntity session;

    @Column(name = "gamemode_key", length = 64, nullable = false)
    private String gamemodeKey;

    @Column(name = "entry_server", length = 64, nullable = false)
    private String entryServer;

    @Column(name = "last_server", length = 64, nullable = false)
    private String lastServer;

    @Column(name = "started_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant startedAt;

    @Column(name = "last_accrued_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant lastAccruedAt;

    @Column(name = "ended_at", columnDefinition = "TIMESTAMP")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 32)
    private PlayerPlaytimeSegmentCloseReason closeReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PlayerPlaytimeSegmentEntity() {
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

    public PlayerSessionEntity getSession() {
        return session;
    }

    public void setSession(PlayerSessionEntity session) {
        this.session = session;
    }

    public String getGamemodeKey() {
        return gamemodeKey;
    }

    public void setGamemodeKey(String gamemodeKey) {
        this.gamemodeKey = gamemodeKey;
    }

    public String getEntryServer() {
        return entryServer;
    }

    public void setEntryServer(String entryServer) {
        this.entryServer = entryServer;
    }

    public String getLastServer() {
        return lastServer;
    }

    public void setLastServer(String lastServer) {
        this.lastServer = lastServer;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastAccruedAt() {
        return lastAccruedAt;
    }

    public void setLastAccruedAt(Instant lastAccruedAt) {
        this.lastAccruedAt = lastAccruedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public PlayerPlaytimeSegmentCloseReason getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(PlayerPlaytimeSegmentCloseReason closeReason) {
        this.closeReason = closeReason;
    }
}
