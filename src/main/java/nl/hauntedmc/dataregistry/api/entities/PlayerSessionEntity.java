package nl.hauntedmc.dataregistry.api.entities;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "player_sessions",
        indexes = {
                @Index(name = "idx_psi_player_started", columnList = "player_id, started_at"),
                @Index(name = "idx_psi_player_open", columnList = "player_id, ended_at")
        }
)
public class PlayerSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many sessions per player
    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    // IPv4/IPv6-ready (nullable, depending on privacy config)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "virtual_host", length = 255)
    private String virtualHost;

    // Set at login; never changes
    @Column(name = "started_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant startedAt;

    // Set at disconnect; null while session is open
    @Column(name = "ended_at", columnDefinition = "TIMESTAMP")
    private Instant endedAt;

    // First backend server the player connects to during this session (set on first ServerConnectedEvent)
    @Column(name = "first_server", length = 64)
    private String firstServer;

    // Last backend server seen during this session (updated on every ServerConnectedEvent)
    @Column(name = "last_server", length = 64)
    private String lastServer;

    // Optional optimistic versioning in case you later add concurrent updates; harmless if unused now
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PlayerSessionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PlayerEntity getPlayer() { return player; }
    public void setPlayer(PlayerEntity player) { this.player = player; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getVirtualHost() { return virtualHost; }
    public void setVirtualHost(String virtualHost) { this.virtualHost = virtualHost; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public String getFirstServer() { return firstServer; }
    public void setFirstServer(String firstServer) { this.firstServer = firstServer; }

    public String getLastServer() { return lastServer; }
    public void setLastServer(String lastServer) { this.lastServer = lastServer; }
}
