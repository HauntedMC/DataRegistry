package nl.hauntedmc.dataregistry.api.playtime;

import java.time.Instant;

/**
 * Ranked playtime entry for leaderboard-style consumers.
 */
public record PlayerPlaytimeLeaderboardEntry(
        long rank,
        Long playerId,
        String playerUuid,
        String username,
        long trackedMillis,
        Instant generatedAt
) {
}
