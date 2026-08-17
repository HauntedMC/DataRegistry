package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import nl.hauntedmc.dataregistry.api.population.PopulationOrdinalQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationScopeType;

import java.time.Instant;

@Entity
@Table(
        name = "player_population_membership",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_population_member_scope_player", columnNames = {"scope_id", "player_id"}),
                @UniqueConstraint(name = "uk_population_member_scope_ordinal", columnNames = {"scope_id", "ordinal_value"})
        },
        indexes = {
                @Index(name = "idx_population_member_player", columnList = "player_id"),
                @Index(name = "idx_population_member_scope", columnList = "scope_id, ordinal_value"),
                @Index(name = "idx_population_member_joined", columnList = "scope_id, first_joined_at")
        }
)
public class PlayerPopulationMembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    @Column(name = "scope_id", length = 80, nullable = false, updatable = false)
    private String scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 16, nullable = false, updatable = false)
    private PopulationScopeType scopeType;

    @Column(name = "scope_key", length = 64, nullable = false, updatable = false)
    private String scopeKey;

    @Column(name = "ordinal_value", nullable = false, updatable = false)
    private long ordinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "ordinal_quality", length = 32, nullable = false)
    private PopulationOrdinalQuality ordinalQuality;

    @Column(name = "first_joined_at", columnDefinition = "TIMESTAMP")
    private Instant firstJoinedAt;

    @Column(name = "first_session_id")
    private Long firstSessionId;

    @Column(name = "first_visit_id")
    private Long firstVisitId;

    @Column(name = "first_lifecycle_event_id", length = 64)
    private String firstLifecycleEventId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    public Long getId() { return id; }
    public PlayerEntity getPlayer() { return player; }
    public void setPlayer(PlayerEntity player) { this.player = player; }
    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
    public PopulationScopeType getScopeType() { return scopeType; }
    public void setScopeType(PopulationScopeType scopeType) { this.scopeType = scopeType; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public long getOrdinal() { return ordinal; }
    public void setOrdinal(long ordinal) { this.ordinal = ordinal; }
    public PopulationOrdinalQuality getOrdinalQuality() { return ordinalQuality; }
    public void setOrdinalQuality(PopulationOrdinalQuality ordinalQuality) { this.ordinalQuality = ordinalQuality; }
    public Instant getFirstJoinedAt() { return firstJoinedAt; }
    public void setFirstJoinedAt(Instant firstJoinedAt) { this.firstJoinedAt = firstJoinedAt; }
    public Long getFirstSessionId() { return firstSessionId; }
    public void setFirstSessionId(Long firstSessionId) { this.firstSessionId = firstSessionId; }
    public Long getFirstVisitId() { return firstVisitId; }
    public void setFirstVisitId(Long firstVisitId) { this.firstVisitId = firstVisitId; }
    public String getFirstLifecycleEventId() { return firstLifecycleEventId; }
    public void setFirstLifecycleEventId(String firstLifecycleEventId) { this.firstLifecycleEventId = firstLifecycleEventId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
