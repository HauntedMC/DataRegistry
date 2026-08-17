package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationScopeType;

import java.time.Instant;

@Entity
@Table(
        name = "population_scope_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_population_scope_type_key",
                columnNames = {"scope_type", "scope_key"}
        ),
        indexes = {
                @Index(name = "idx_population_scope_type", columnList = "scope_type"),
                @Index(name = "idx_population_scope_updated", columnList = "updated_at")
        }
)
public class PopulationScopeStateEntity {

    @Id
    @Column(name = "scope_id", length = 80, nullable = false, updatable = false)
    private String scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 16, nullable = false, updatable = false)
    private PopulationScopeType scopeType;

    @Column(name = "scope_key", length = 64, nullable = false, updatable = false)
    private String scopeKey;

    @Column(name = "unique_player_count", nullable = false)
    private long uniquePlayerCount;

    @Column(name = "current_online", nullable = false)
    private long currentOnline;

    @Column(name = "online_peak", nullable = false)
    private long onlinePeak;

    @Column(name = "online_peak_achieved_at", columnDefinition = "TIMESTAMP")
    private Instant onlinePeakAchievedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_baseline_quality", length = 24, nullable = false)
    private PopulationBaselineQuality membershipBaselineQuality;

    @Enumerated(EnumType.STRING)
    @Column(name = "peak_baseline_quality", length = 24, nullable = false)
    private PopulationBaselineQuality peakBaselineQuality;

    @Column(name = "backfill_version", nullable = false)
    private int backfillVersion;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
    public PopulationScopeType getScopeType() { return scopeType; }
    public void setScopeType(PopulationScopeType scopeType) { this.scopeType = scopeType; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public long getUniquePlayerCount() { return uniquePlayerCount; }
    public void setUniquePlayerCount(long uniquePlayerCount) { this.uniquePlayerCount = uniquePlayerCount; }
    public long getCurrentOnline() { return currentOnline; }
    public void setCurrentOnline(long currentOnline) { this.currentOnline = currentOnline; }
    public long getOnlinePeak() { return onlinePeak; }
    public void setOnlinePeak(long onlinePeak) { this.onlinePeak = onlinePeak; }
    public Instant getOnlinePeakAchievedAt() { return onlinePeakAchievedAt; }
    public void setOnlinePeakAchievedAt(Instant onlinePeakAchievedAt) { this.onlinePeakAchievedAt = onlinePeakAchievedAt; }
    public PopulationBaselineQuality getMembershipBaselineQuality() { return membershipBaselineQuality; }
    public void setMembershipBaselineQuality(PopulationBaselineQuality value) { membershipBaselineQuality = value; }
    public PopulationBaselineQuality getPeakBaselineQuality() { return peakBaselineQuality; }
    public void setPeakBaselineQuality(PopulationBaselineQuality value) { peakBaselineQuality = value; }
    public int getBackfillVersion() { return backfillVersion; }
    public void setBackfillVersion(int backfillVersion) { this.backfillVersion = backfillVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
