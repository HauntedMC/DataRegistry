package nl.hauntedmc.dataregistry.api.player;

import java.time.Instant;

/**
 * Immutable summary of a player's first and latest observed activity.
 *
 * @param playerId     stable DataRegistry player id.
 * @param firstSeenAt  first time DataRegistry observed the player.
 * @param lastSeenAt   latest activity timestamp known to DataRegistry.
 * @param lastLoginAt  latest login timestamp, when tracked.
 * @param lastLogoutAt latest logout timestamp, when tracked.
 */
public record PlayerActivitySnapshot(
        long playerId,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant lastLoginAt,
        Instant lastLogoutAt
) {
}
