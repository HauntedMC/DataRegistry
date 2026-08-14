package nl.hauntedmc.dataregistry.api.playtime;

import java.time.Instant;

/**
 * Durable lifecycle and playtime information for one player in one logical gamemode.
 */
public record PlayerGamemodeActivitySnapshot(
        Long playerId,
        String playerUuid,
        String username,
        String gamemodeKey,
        long trackedMillis,
        boolean countedTowardsNetworkTotal,
        long segmentCount,
        Instant firstJoinedAt,
        Instant lastJoinedAt,
        Instant lastExitedAt,
        Instant lastLogoutAt,
        boolean active,
        Instant activeSince,
        String activeServerName,
        Instant generatedAt,
        boolean lifecycleHistoryComplete
) {
}
