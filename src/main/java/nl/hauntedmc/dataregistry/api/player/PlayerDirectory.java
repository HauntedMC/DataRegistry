package nl.hauntedmc.dataregistry.api.player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Read-oriented facade for canonical player identities.
 * <p>
 * Feature plugins should use this API when they only need a player's stable
 * database id, UUID, or current username. Player creation and username updates
 * remain owned by DataRegistry lifecycle handling.
 * <p>
 * Lookup methods may perform database I/O and should be called from an async
 * context when used on Bukkit/Paper or Velocity event paths. {@link #whenReady(UUID)}
 * is safe to call from event handlers because it only observes active lifecycle state.
 */
public interface PlayerDirectory {
    /**
     * Returns the active cached identity for a player without querying persistence.
     */
    Optional<PlayerIdentity> getActiveIdentity(UUID uuid);

    /**
     * Returns the active cached identity for a UUID string without querying persistence.
     */
    Optional<PlayerIdentity> getActiveIdentity(String uuid);

    /**
     * Looks up a persisted identity by UUID without creating or updating a player row.
     */
    Optional<PlayerIdentity> findByUuid(UUID uuid);

    /**
     * Looks up a persisted identity by UUID string without creating or updating a player row.
     */
    Optional<PlayerIdentity> findByUuid(String uuid);

    /**
     * Looks up a persisted identity by exact username without creating or updating a player row.
     */
    Optional<PlayerIdentity> findByUsername(String username);

    /**
     * Looks up a persisted identity by case-insensitive username without creating or updating a player row.
     */
    Optional<PlayerIdentity> findByUsernameIgnoreCase(String username);

    /**
     * Returns active identity snapshots keyed by normalized UUID string.
     */
    Map<String, PlayerIdentity> snapshotActiveIdentities();

    /**
     * Completes when DataRegistry's platform lifecycle has finished preparing this identity.
     * <p>
     * The returned future does not perform database work itself. If no lifecycle preparation is
     * pending and no active identity exists, it completes with {@link Optional#empty()}.
     */
    CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid);

    /**
     * Completes when DataRegistry's platform lifecycle has finished preparing this identity.
     * <p>
     * Invalid UUID strings complete immediately with {@link Optional#empty()}.
     */
    CompletableFuture<Optional<PlayerIdentity>> whenReady(String uuid);
}
