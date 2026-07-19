package nl.hauntedmc.dataregistry.api.player;

import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-oriented facade for canonical player identities.
 * <p>
 * Feature plugins should use this API when they only need a player's stable
 * database id, UUID, or current username. Player creation and username updates
 * remain owned by DataRegistry lifecycle handling.
 */
public final class PlayerDirectory {

    private final PlayerRepository playerRepository;
    private final ConcurrentMap<String, CompletableFuture<Optional<PlayerIdentity>>> identityReadiness =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public PlayerDirectory(PlayerRepository playerRepository) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
    }

    /**
     * Returns the active cached identity for a player without querying persistence.
     */
    public Optional<PlayerIdentity> getActiveIdentity(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return playerRepository.getActiveIdentity(normalizedUuid);
    }

    /**
     * Looks up a persisted identity by UUID without creating or updating a player row.
     */
    public Optional<PlayerIdentity> findByUuid(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return playerRepository.findIdentityByUUID(normalizedUuid);
    }

    /**
     * Looks up a persisted identity by exact username without creating or updating a player row.
     */
    public Optional<PlayerIdentity> findByUsername(String username) {
        return playerRepository.findIdentityByUsername(username);
    }

    /**
     * Looks up a persisted identity by case-insensitive username without creating or updating a player row.
     */
    public Optional<PlayerIdentity> findByUsernameIgnoreCase(String username) {
        return playerRepository.findIdentityByUsernameIgnoreCase(username);
    }

    /**
     * Returns active identity snapshots keyed by normalized UUID string.
     */
    public Map<String, PlayerIdentity> snapshotActiveIdentities() {
        return playerRepository.snapshotActiveIdentities();
    }

    /**
     * Completes when DataRegistry's platform lifecycle has finished preparing this identity.
     * <p>
     * The returned future does not perform database work itself. If no lifecycle preparation is
     * pending and no active identity exists, it completes with {@link Optional#empty()}.
     */
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> activeIdentity = getActiveIdentity(uuid);
        if (activeIdentity.isPresent()) {
            return CompletableFuture.completedFuture(activeIdentity);
        }
        CompletableFuture<Optional<PlayerIdentity>> pending = identityReadiness.get(normalizedUuid);
        if (pending != null) {
            return pending.copy();
        }
        if (closed.get()) {
            CompletableFuture<Optional<PlayerIdentity>> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("DataRegistry is shutting down."));
            return failed;
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Starts identity readiness tracking for a platform join lifecycle.
     */
    public CompletableFuture<Optional<PlayerIdentity>> beginIdentityInitialization(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null || closed.get()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return identityReadiness.compute(normalizedUuid, (key, existing) -> {
            if (existing == null || existing.isDone()) {
                return new CompletableFuture<>();
            }
            return existing;
        }).copy();
    }

    /**
     * Marks the player's identity as available to dependent features.
     */
    public void completeIdentityReady(PlayerIdentity identity) {
        if (identity == null) {
            return;
        }
        String normalizedUuid = identity.uuid().toString();
        CompletableFuture<Optional<PlayerIdentity>> pending = identityReadiness.remove(normalizedUuid);
        if (pending != null) {
            pending.complete(Optional.of(identity));
        }
    }

    /**
     * Marks identity preparation as unavailable, usually because the player left first.
     */
    public void completeIdentityUnavailable(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return;
        }
        CompletableFuture<Optional<PlayerIdentity>> pending = identityReadiness.remove(normalizedUuid);
        if (pending != null) {
            pending.complete(Optional.empty());
        }
    }

    /**
     * Marks identity preparation as failed.
     */
    public void failIdentityInitialization(UUID uuid, Throwable failure) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return;
        }
        CompletableFuture<Optional<PlayerIdentity>> pending = identityReadiness.remove(normalizedUuid);
        if (pending != null) {
            pending.completeExceptionally(
                    failure == null ? new IllegalStateException("Player identity initialization failed.") : failure
            );
        }
    }

    /**
     * Cancels all outstanding readiness waiters during plugin shutdown.
     */
    public void shutdown() {
        closed.set(true);
        CancellationException cancellation = new CancellationException("DataRegistry is shutting down.");
        identityReadiness.forEach((uuid, future) -> future.completeExceptionally(cancellation));
        identityReadiness.clear();
    }

    private static String normalizeUuid(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }
}
