package nl.hauntedmc.dataregistry.api.population;

import java.time.Instant;
import java.util.Objects;

/** Immutable durable population transition. */
public record PopulationTransition(
        long id,
        PopulationTransitionType type,
        PopulationTransitionCause cause,
        PopulationScope scope,
        Long playerId,
        String serverName,
        Long ordinal,
        long previousValue,
        long currentValue,
        Instant occurredAt
) {
    public PopulationTransition {
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be positive.");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(cause, "cause must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (previousValue < 0L || currentValue < 0L) {
            throw new IllegalArgumentException("Population transition values must not be negative.");
        }
        if (ordinal != null && ordinal <= 0L) {
            throw new IllegalArgumentException("ordinal must be positive when present.");
        }
    }
}
