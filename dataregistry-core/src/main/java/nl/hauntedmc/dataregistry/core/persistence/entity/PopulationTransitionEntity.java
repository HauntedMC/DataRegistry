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
import nl.hauntedmc.dataregistry.api.population.PopulationScopeType;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionCause;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionType;

import java.time.Instant;

@Entity
@Table(
        name = "population_transition",
        indexes = {
                @Index(name = "idx_population_transition_scope", columnList = "scope_id, id"),
                @Index(name = "idx_population_transition_type", columnList = "transition_type, id"),
                @Index(name = "idx_population_transition_cause", columnList = "transition_cause, id"),
                @Index(name = "idx_population_transition_time", columnList = "occurred_at, id")
        }
)
public class PopulationTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_type", length = 32, nullable = false, updatable = false)
    private PopulationTransitionType transitionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_cause", length = 24, nullable = false, updatable = false)
    private PopulationTransitionCause transitionCause;

    @Column(name = "scope_id", length = 80, nullable = false, updatable = false)
    private String scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 16, nullable = false, updatable = false)
    private PopulationScopeType scopeType;

    @Column(name = "scope_key", length = 64, nullable = false, updatable = false)
    private String scopeKey;

    @Column(name = "player_id", updatable = false)
    private Long playerId;

    @Column(name = "server_name", length = 64, updatable = false)
    private String serverName;

    @Column(name = "ordinal_value", updatable = false)
    private Long ordinal;

    @Column(name = "previous_value", nullable = false, updatable = false)
    private long previousValue;

    @Column(name = "current_value", nullable = false, updatable = false)
    private long currentValue;

    @Column(name = "occurred_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    public Long getId() { return id; }
    public PopulationTransitionType getTransitionType() { return transitionType; }
    public void setTransitionType(PopulationTransitionType value) { transitionType = value; }
    public PopulationTransitionCause getTransitionCause() { return transitionCause; }
    public void setTransitionCause(PopulationTransitionCause value) { transitionCause = value; }
    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
    public PopulationScopeType getScopeType() { return scopeType; }
    public void setScopeType(PopulationScopeType scopeType) { this.scopeType = scopeType; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public Long getOrdinal() { return ordinal; }
    public void setOrdinal(Long ordinal) { this.ordinal = ordinal; }
    public long getPreviousValue() { return previousValue; }
    public void setPreviousValue(long previousValue) { this.previousValue = previousValue; }
    public long getCurrentValue() { return currentValue; }
    public void setCurrentValue(long currentValue) { this.currentValue = currentValue; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
