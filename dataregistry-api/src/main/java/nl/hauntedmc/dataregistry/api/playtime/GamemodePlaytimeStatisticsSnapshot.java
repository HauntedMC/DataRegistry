package nl.hauntedmc.dataregistry.api.playtime;

import java.time.Instant;

/**
 * Network-wide aggregate statistics for one logical gamemode.
 */
public record GamemodePlaytimeStatisticsSnapshot(
        String gamemodeKey,
        long uniquePlayerCount,
        long trackedMillis,
        long segmentCount,
        Instant firstJoinedAt,
        Instant lastActivityAt,
        boolean countedTowardsNetworkTotal,
        Instant generatedAt
) {
}
