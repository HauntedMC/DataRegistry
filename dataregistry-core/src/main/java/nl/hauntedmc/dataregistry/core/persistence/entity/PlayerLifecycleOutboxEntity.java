package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Durable outbox row written in the same transaction as player lifecycle state.
 */
@Entity
@Table(
        name = "player_lifecycle_outbox",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_plo_event_id", columnNames = {"event_id"})
        },
        indexes = {
                @Index(name = "idx_plo_type_created", columnList = "event_type, created_at"),
                @Index(name = "idx_plo_player_created", columnList = "player_id, created_at"),
                @Index(name = "idx_plo_unpublished", columnList = "published_at, created_at")
        }
)
public class PlayerLifecycleOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 96)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private PlayerLifecycleOutboxEventType eventType;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    @Column(name = "player_uuid", nullable = false, updatable = false, length = 36)
    private String playerUuid;

    @Column(name = "username", nullable = false, updatable = false, length = 32)
    private String username;

    @Column(name = "server_name", updatable = false, length = 64)
    private String serverName;

    @Column(name = "occurred_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "published_at", columnDefinition = "TIMESTAMP")
    private Instant publishedAt;

    public PlayerLifecycleOutboxEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public PlayerLifecycleOutboxEventType getEventType() {
        return eventType;
    }

    public void setEventType(PlayerLifecycleOutboxEventType eventType) {
        this.eventType = eventType;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
