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
@Table(name = "player_connection_info",
        indexes = {
                @Index(name = "idx_pci_last_conn_at", columnList = "last_connection_at"),
                @Index(name = "idx_pci_last_disc_at", columnList = "last_disconnect_at")
        })

public class PlayerConnectionInfoEntity {

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

    @Column(name = "ip_address", length = 45)  // nullable by design
    private String ipAddress;

    @Column(name = "first_connection_at", columnDefinition = "TIMESTAMP", updatable = false)
    private Instant firstConnectionAt;

    @Column(name = "last_connection_at", columnDefinition = "TIMESTAMP")
    private Instant lastConnectionAt;

    @Column(name = "last_disconnect_at", columnDefinition = "TIMESTAMP")
    private Instant lastDisconnectAt;

    @Column(name = "virtual_host", length = 255)
    private String virtualHost;

    public PlayerConnectionInfoEntity() {}

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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getFirstConnectionAt() {
        return firstConnectionAt;
    }

    public void setFirstConnectionAt(Instant firstConnectionAt) {
        this.firstConnectionAt = firstConnectionAt;
    }

    public Instant getLastConnectionAt() {
        return lastConnectionAt;
    }

    public void setLastConnectionAt(Instant lastConnectionAt) {
        this.lastConnectionAt = lastConnectionAt;
    }

    public Instant getLastDisconnectAt() {
        return lastDisconnectAt;
    }

    public void setLastDisconnectAt(Instant lastDisconnectAt) {
        this.lastDisconnectAt = lastDisconnectAt;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }
}
