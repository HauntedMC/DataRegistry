package nl.hauntedmc.dataregistry.api.playtime;

import java.time.Instant;

/**
 * Centrally stored playtime policy for a logical gamemode.
 */
public record TrackedGamemodeSnapshot(
        String gamemodeKey,
        boolean countedTowardsNetworkTotal,
        Instant firstObservedAt
) {
}
