package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import nl.hauntedmc.dataregistry.api.player.PlayerDataVisibility;

/** Explicit non-public player disclosure settings. Missing rows represent {@link PlayerDataVisibility#PUBLIC}. */
@Entity
@Table(name = "player_privacy")
public class PlayerPrivacyEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private PlayerDataVisibility visibility;

    public PlayerPrivacyEntity() {
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

    public PlayerDataVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(PlayerDataVisibility visibility) {
        this.visibility = visibility;
    }
}
