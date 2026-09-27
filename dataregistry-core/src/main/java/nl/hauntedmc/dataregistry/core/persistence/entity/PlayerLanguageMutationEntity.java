package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Durable idempotency receipt for a language preference command. */
@Entity
@Table(name = "player_language_mutation", indexes = @Index(name = "idx_player_language_mutation_created", columnList = "created_at"))
public class PlayerLanguageMutationEntity {
    @Id
    @Column(name = "request_id", length = 36)
    private String requestId;
    @Column(name = "player_id", nullable = false)
    private long playerId;
    @Column(name = "player_uuid", nullable = false, length = 36)
    private String playerUuid;
    @Column(name = "preference", nullable = false, length = 16)
    private String preference;
    @Column(name = "expected_version", nullable = false)
    private long expectedVersion;
    @Column(name = "result_version", nullable = false)
    private long resultVersion;
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected PlayerLanguageMutationEntity() { }

    public PlayerLanguageMutationEntity(String requestId, long playerId, String playerUuid,
                                        String preference, long expectedVersion, long resultVersion, long createdAt) {
        this.requestId = requestId;
        this.playerId = playerId;
        this.playerUuid = playerUuid;
        this.preference = preference;
        this.expectedVersion = expectedVersion;
        this.resultVersion = resultVersion;
        this.createdAt = createdAt;
    }

    public long getPlayerId() { return playerId; }
    public String getPlayerUuid() { return playerUuid; }
    public String getPreference() { return preference; }
    public long getExpectedVersion() { return expectedVersion; }
    public long getResultVersion() { return resultVersion; }
    public long getCreatedAt() { return createdAt; }
}
