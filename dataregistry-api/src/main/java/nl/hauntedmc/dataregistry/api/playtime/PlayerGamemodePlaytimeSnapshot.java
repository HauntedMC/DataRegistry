package nl.hauntedmc.dataregistry.api.playtime;

import java.time.Instant;

/**
 * Read-side snapshot of a player's playtime within one logical gamemode.
 */
public record PlayerGamemodePlaytimeSnapshot(
        String gamemodeKey,
        long trackedMillis,
        boolean countedTowardsNetworkTotal,
        boolean active,
        Instant activeSince,
        String activeServerName,
        Instant firstTrackedAt,
        Instant lastTrackedAt,
        long segmentCount
) {
}
