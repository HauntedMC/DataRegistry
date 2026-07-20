package nl.hauntedmc.dataregistry.api.player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Read-oriented facade for canonical player identities.
 * <p>
 * Public persistence reads are asynchronous by construction. Returned stages are backed by
 * DataRegistry's query executor and may complete exceptionally when cancelled or when the configured
 * query deadline is exceeded. Completion callbacks run on the thread that completes the stage unless
 * callers explicitly choose an async continuation executor.
 */
public interface PlayerDirectory {
    /**
     * Returns the active cached identity for a player without querying persistence.
     */
    Optional<PlayerIdentity> findActiveIdentityCached(UUID uuid);

    /**
     * Returns the active cached identity for a UUID string without querying persistence.
     */
    Optional<PlayerIdentity> findActiveIdentityCached(String uuid);

    /**
     * @deprecated use {@link #findActiveIdentityCached(UUID)} to make the cache-only behavior explicit.
     */
    @Deprecated(forRemoval = true)
    default Optional<PlayerIdentity> getActiveIdentity(UUID uuid) {
        return findActiveIdentityCached(uuid);
    }

    /**
     * @deprecated use {@link #findActiveIdentityCached(String)} to make the cache-only behavior explicit.
     */
    @Deprecated(forRemoval = true)
    default Optional<PlayerIdentity> getActiveIdentity(String uuid) {
        return findActiveIdentityCached(uuid);
    }

    /**
     * Looks up a persisted identity by typed lookup key without creating or updating a player row.
     */
    CompletionStage<Optional<PlayerIdentity>> findIdentity(PlayerLookup lookup);

    /**
     * Resolves multiple persisted identities in one query task.
     */
    CompletionStage<Map<PlayerLookup, Optional<PlayerIdentity>>> findIdentities(Collection<PlayerLookup> lookups);

    /**
     * Looks up a persisted identity by UUID without creating or updating a player row.
     */
    CompletionStage<Optional<PlayerIdentity>> findByUuid(UUID uuid);

    /**
     * Looks up a persisted identity by stable DataRegistry player id.
     */
    CompletionStage<Optional<PlayerIdentity>> findByPlayerId(long playerId);

    /**
     * Looks up a persisted identity by UUID string without creating or updating a player row.
     */
    CompletionStage<Optional<PlayerIdentity>> findByUuid(String uuid);

    /**
     * Looks up a persisted identity by exact username without creating or updating a player row.
     */
    CompletionStage<Optional<PlayerIdentity>> findByUsername(String username);

    /**
     * Looks up a persisted identity by case-insensitive username without creating or updating a player row.
     */
    CompletionStage<Optional<PlayerIdentity>> findByUsernameIgnoreCase(String username);

    /**
     * Looks up a persisted identity by UUID string or case-insensitive username.
     */
    CompletionStage<Optional<PlayerIdentity>> findByIdentifier(String identifier);

    /**
     * Finds persisted identities whose current username starts with {@code prefix}, case-insensitively.
     */
    CompletionStage<List<PlayerIdentity>> findByUsernamePrefix(String prefix, int limit);

    /**
     * Finds a cursor page of identities whose current username starts with {@code prefix}, case-insensitively.
     */
    CompletionStage<PlayerPage<PlayerIdentity>> findByUsernamePrefix(String prefix, PlayerPageRequest pageRequest);

    /**
     * Returns active identity snapshots keyed by normalized UUID string.
     */
    Map<String, PlayerIdentity> snapshotActiveIdentities();

    /**
     * Completes when DataRegistry's platform lifecycle has finished preparing this identity.
     * <p>
     * The returned future does not perform database work itself. If no lifecycle preparation is
     * pending and no active identity exists, it completes with {@link Optional#empty()}.
     * Completion callbacks may run on a DataRegistry lifecycle thread.
     */
    CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid);

    /**
     * Completes when DataRegistry's platform lifecycle has finished preparing this identity.
     * <p>
     * Invalid UUID strings complete immediately with {@link Optional#empty()}. Completion callbacks may
     * run on a DataRegistry lifecycle thread.
     */
    CompletableFuture<Optional<PlayerIdentity>> whenReady(String uuid);
}
