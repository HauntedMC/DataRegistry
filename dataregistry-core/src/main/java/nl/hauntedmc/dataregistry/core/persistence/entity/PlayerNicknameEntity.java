package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_nicknames")
public class PlayerNicknameEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    public PlayerNicknameEntity() {
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
