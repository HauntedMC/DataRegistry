package nl.hauntedmc.dataregistry.api.population;

import java.time.Instant;
import java.util.Objects;

/** Immutable aggregate population state for one scope. */
public record PopulationSnapshot(
        PopulationScope scope,
        long uniquePlayerCount,
        long currentOnline,
        long onlinePeak,
        Instant onlinePeakAchievedAt,
        PopulationBaselineQuality membershipBaselineQuality,
        PopulationBaselineQuality peakBaselineQuality,
        Instant generatedAt
) {
    public PopulationSnapshot {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(membershipBaselineQuality, "membershipBaselineQuality must not be null");
        Objects.requireNonNull(peakBaselineQuality, "peakBaselineQuality must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        if (uniquePlayerCount < 0L || currentOnline < 0L || onlinePeak < 0L) {
            throw new IllegalArgumentException("Population counts must not be negative.");
        }
        if (onlinePeak < currentOnline) {
            throw new IllegalArgumentException("onlinePeak must be at least currentOnline.");
        }
    }
}
