package nl.hauntedmc.dataregistry.core.persistence.entity;

import nl.hauntedmc.dataregistry.api.session.SessionFence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_online_status")
public class PlayerOnlineStatusEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    @Column(name = "online", nullable = false)
    private boolean online;

    @Column(name = "current_server", length = 64, nullable = false)
    private String currentServer = "";

    @Column(name = "previous_server", length = 64)
    private String previousServer;

    @Column(name = "proxy_instance_id", length = 96, nullable = false)
    private String proxyInstanceId = "";

    @Column(name = "proxy_process_epoch", length = 36, nullable = false)
    private String proxyProcessEpoch = "";

    @Column(name = "network_session_id", length = 36, nullable = false)
    private String networkSessionId = "";

    @Column(name = "network_session_epoch", nullable = false)
    private long networkSessionEpoch;

    @Column(name = "network_fencing_token", nullable = false)
    private long networkFencingToken;

    public PlayerOnlineStatusEntity() {
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

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getCurrentServer() {
        return currentServer;
    }

    public void setCurrentServer(String currentServer) {
        this.currentServer = currentServer;
    }

    public String getPreviousServer() {
        return previousServer;
    }

    public void setPreviousServer(String previousServer) {
        this.previousServer = previousServer;
    }

    public void setSessionFence(SessionFence fence) {
        proxyInstanceId = fence.proxyInstanceId();
        proxyProcessEpoch = fence.proxyProcessEpoch().toString();
        networkSessionId = fence.sessionId().toString();
        networkSessionEpoch = fence.sessionEpoch();
        networkFencingToken = fence.fencingToken();
    }

    public boolean matches(SessionFence fence) {
        return fence != null
                && proxyInstanceId.equals(fence.proxyInstanceId())
                && proxyProcessEpoch.equals(fence.proxyProcessEpoch().toString())
                && networkSessionId.equals(fence.sessionId().toString())
                && networkSessionEpoch == fence.sessionEpoch()
                && networkFencingToken == fence.fencingToken();
    }

    public long getNetworkFencingToken() { return networkFencingToken; }
}
