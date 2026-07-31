package nl.hauntedmc.dataregistry.api.playtime;

import java.time.Instant;
import java.util.List;

/**
 * Public read-side snapshot of a player's queryable tracked playtime.
 * <p>
 * Both totals and the gamemode list omit query-blacklisted gamemodes. {@code networkTotalMillis} additionally omits
 * queryable gamemodes configured not to count toward the public network total.
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
