package nl.hauntedmc.dataregistry.api.player;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable username-history entry for a DataRegistry player.
 *
 * @param id         history row id.
 * @param playerId   stable DataRegistry player id.
 * @param username   username observed for the player.
 * @param lastSeenAt time the username was last observed.
 */
public record PlayerNameHistoryEntry(long id, long playerId, String username, Instant lastSeenAt) {

    /**
     * Creates a validated username-history snapshot.
     */
    public PlayerNameHistoryEntry {
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be a positive database id.");
        }
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
    }
}
