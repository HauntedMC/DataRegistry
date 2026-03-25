package nl.hauntedmc.dataregistry.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_language")
public class PlayerLanguageEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    /**
     * Store the language as a plain code (e.g., "NL", "EN"), NOT as an enum,
     * so this module does not depend on commonlib.
     */
    @Column(name = "language", nullable = false, length = 16)
    private String language;

    public PlayerLanguageEntity() {}

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public PlayerEntity getPlayer() { return player; }
    public void setPlayer(PlayerEntity player) { this.player = player; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
