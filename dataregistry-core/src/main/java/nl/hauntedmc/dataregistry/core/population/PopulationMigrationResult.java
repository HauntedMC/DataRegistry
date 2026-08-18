package nl.hauntedmc.dataregistry.core.population;

import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;

/** Result of the idempotent population metadata/backfill pass. */
public record PopulationMigrationResult(
        long networkMembershipsAdded,
        long gamemodeMembershipsAdded,
        PopulationBaselineQuality baselineQuality,
        boolean migrationApplied
) {
}
