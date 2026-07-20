package nl.hauntedmc.dataregistry.api.playtime;

import java.time.Instant;
import java.util.List;

/**
 * Read-side snapshot of a player's tracked playtime.
 */
public record PlayerPlaytimeSnapshot(
        Long playerId,
        String playerUuid,
        String username,
        long trackedTotalMillis,
        long networkTotalMillis,
        Instant generatedAt,
        List<PlayerGamemodePlaytimeSnapshot> gamemodes
) {
}
