package nl.hauntedmc.dataregistry.api.player;

import java.time.Instant;

/**
 * Immutable read-side view of DataRegistry's latest connection metadata for a player.
 *
 * @param playerId          stable DataRegistry player id.
 * @param ipAddress         latest stored IP address, when privacy settings allow it.
 * @param firstConnectionAt first connection timestamp.
 * @param lastConnectionAt  latest connection timestamp.
 * @param lastDisconnectAt  latest disconnect timestamp.
 * @param virtualHost       latest stored virtual host, when privacy settings allow it.
 */
public record PlayerConnectionSnapshot(
        long playerId,
        String ipAddress,
        Instant firstConnectionAt,
        Instant lastConnectionAt,
        Instant lastDisconnectAt,
        String virtualHost
) {
}
