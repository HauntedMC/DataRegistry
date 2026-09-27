package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Committed notification awaiting publication; payload contains no player setting value. */
@Entity
@Table(name = "player_data_change_outbox", indexes = @Index(name = "idx_player_data_change_unpublished", columnList = "published_at,created_at"))
public class PlayerDataChangeOutboxEntity {
    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;
    @Column(name = "capability", nullable = false, length = 64)
    private String capability;
    @Column(name = "player_id", nullable = false)
    private long playerId;
    @Column(name = "player_uuid", nullable = false, length = 36)
    private String playerUuid;
    @Column(name = "revision", nullable = false)
    private long revision;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "published_at")
    private Long publishedAt;

    protected PlayerDataChangeOutboxEntity() { }

    public PlayerDataChangeOutboxEntity(String eventId, String capability, long playerId, String playerUuid,
                                       long revision, long createdAt) {
        this.eventId = eventId;
        this.capability = capability;
        this.playerId = playerId;
        this.playerUuid = playerUuid;
        this.revision = revision;
        this.createdAt = createdAt;
    }

    public String getEventId() { return eventId; }
    public String getCapability() { return capability; }
    public long getPlayerId() { return playerId; }
    public String getPlayerUuid() { return playerUuid; }
    public long getRevision() { return revision; }
    public long getCreatedAt() { return createdAt; }
    public Long getPublishedAt() { return publishedAt; }
    public void markPublished(long timestamp) { publishedAt = timestamp; }
}
