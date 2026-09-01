package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import nl.hauntedmc.dataregistry.api.session.SessionFence;

import java.time.Instant;
import java.util.Objects;

/** Mandatory durable latest-session fence, independent of optional status and history domains. */
@Entity
@Table(name = "player_lifecycle_authority")
public class PlayerLifecycleAuthorityEntity {
    @Id
    @Column(name = "player_id")
    private Long playerId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    @Column(name = "proxy_instance_id", length = 96, nullable = false)
    private String proxyInstanceId;

    @Column(name = "proxy_process_epoch", length = 36, nullable = false)
    private String proxyProcessEpoch;

    @Column(name = "network_session_id", length = 36, nullable = false)
    private String networkSessionId;

    @Column(name = "network_session_epoch", nullable = false)
    private long networkSessionEpoch;

    @Column(name = "network_fencing_token", nullable = false)
    private long networkFencingToken;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PlayerLifecycleAuthorityEntity() { }

    public Long getPlayerId() { return playerId; }
    public PlayerEntity getPlayer() { return player; }
    public void setPlayer(PlayerEntity player) { this.player = Objects.requireNonNull(player, "player"); }
    public boolean isActive() { return active; }
    public long getNetworkFencingToken() { return networkFencingToken; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Claims only a strictly newer Redis fencing token. */
    public boolean claim(SessionFence fence, Instant changedAt) {
        Objects.requireNonNull(fence, "fence");
        if (networkFencingToken >= fence.fencingToken()) return false;
        apply(fence);
        active = true;
        updatedAt = Objects.requireNonNull(changedAt, "changedAt");
        return true;
    }

    public boolean matches(SessionFence fence) {
        return fence != null
                && proxyInstanceId.equals(fence.proxyInstanceId())
                && proxyProcessEpoch.equals(fence.proxyProcessEpoch().toString())
                && networkSessionId.equals(fence.sessionId().toString())
                && networkSessionEpoch == fence.sessionEpoch()
                && networkFencingToken == fence.fencingToken();
    }

    public void deactivate(SessionFence fence, Instant changedAt) {
        if (!active || !matches(fence)) throw new IllegalStateException("lifecycle authority fence mismatch");
        active = false;
        updatedAt = Objects.requireNonNull(changedAt, "changedAt");
    }

    private void apply(SessionFence fence) {
        proxyInstanceId = fence.proxyInstanceId();
        proxyProcessEpoch = fence.proxyProcessEpoch().toString();
        networkSessionId = fence.sessionId().toString();
        networkSessionEpoch = fence.sessionEpoch();
        networkFencingToken = fence.fencingToken();
    }
}
