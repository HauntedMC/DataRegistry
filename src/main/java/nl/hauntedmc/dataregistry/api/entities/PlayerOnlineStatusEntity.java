package nl.hauntedmc.dataregistry.api.entities;

import jakarta.persistence.*;

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
}
