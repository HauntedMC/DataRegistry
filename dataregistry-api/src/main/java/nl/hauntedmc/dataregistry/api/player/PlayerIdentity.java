package nl.hauntedmc.dataregistry.api.player;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable read-side view of a canonical DataRegistry player identity.
 *
 * @param playerId DataRegistry database id for the player.
 * @param uuid     Minecraft UUID for the player.
 * @param username Last username stored by the authoritative DataRegistry lifecycle.
 */
public record PlayerIdentity(Long playerId, UUID uuid, String username) {

    /**
     * Creates a validated identity snapshot.
     */
    public PlayerIdentity {
        if (playerId == null || playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
        Objects.requireNonNull(uuid, "uuid must not be null");
        Objects.requireNonNull(username, "username must not be null");
    }
}
