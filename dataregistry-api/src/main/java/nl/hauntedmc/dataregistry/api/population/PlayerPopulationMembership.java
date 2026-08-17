package nl.hauntedmc.dataregistry.api.population;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable membership and ordinal for one player in one population scope. */
public record PlayerPopulationMembership(
        long playerId,
        UUID uuid,
        String username,
        PopulationScope scope,
        long ordinal,
        PopulationOrdinalQuality ordinalQuality,
        Instant firstJoinedAt,
        Instant createdAt
) {
    public PlayerPopulationMembership {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be positive.");
        }
        Objects.requireNonNull(uuid, "uuid must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        if (ordinal <= 0L) {
            throw new IllegalArgumentException("ordinal must be positive.");
        }
        Objects.requireNonNull(ordinalQuality, "ordinalQuality must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
