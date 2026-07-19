package nl.hauntedmc.dataregistry.backend.lifecycle;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Tracks the DataRegistry-owned initialization attempt for each joining player.
 * <p>
 * Platform listeners start an initialization when a join/login event enters DataRegistry. The returned
 * {@link PlayerIdentityInitialization} handle is then required to publish success, unavailability, or failure.
 * This prevents stale asynchronous work from an earlier connection from completing a future owned by a later
 * reconnect with the same UUID.
 */
public final class PlayerIdentityInitializationTracker {

    private final ConcurrentMap<String, PlayerIdentityInitialization> pendingInitializations =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Returns a future that completes when the requested identity is available or known unavailable.
     *
     * @param uuid                   player UUID to wait for.
     * @param activeIdentitySupplier lookup used to return already-active identities immediately.
     * @return a defensive future copy for the requested identity state.
     */
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(
            UUID uuid,
            Supplier<Optional<PlayerIdentity>> activeIdentitySupplier
    ) {
        Objects.requireNonNull(activeIdentitySupplier, "activeIdentitySupplier must not be null");
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        Optional<PlayerIdentity> activeIdentity;
        try {
            activeIdentity = activeIdentitySupplier.get();
        } catch (RuntimeException exception) {
            CompletableFuture<Optional<PlayerIdentity>> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
        if (activeIdentity != null && activeIdentity.isPresent()) {
            return CompletableFuture.completedFuture(activeIdentity);
        }

        PlayerIdentityInitialization pending = pendingInitializations.get(uuid.toString());
        if (pending != null) {
            return pending.future();
        }
        if (closed.get()) {
            CompletableFuture<Optional<PlayerIdentity>> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("DataRegistry is shutting down."));
            return failed;
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Starts a new identity initialization attempt for a player join/login lifecycle.
     *
     * @param uuid player UUID being initialized.
     * @return handle that must be used to complete this specific initialization attempt.
     */
    public PlayerIdentityInitialization begin(UUID uuid) {
        if (uuid == null || closed.get()) {
            return PlayerIdentityInitialization.untracked(uuid);
        }

        PlayerIdentityInitialization initialization = PlayerIdentityInitialization.pending(uuid);
        pendingInitializations.compute(uuid.toString(), (key, existing) -> {
            if (existing != null) {
                existing.completeUnavailable();
            }
            return initialization;
        });
        return initialization;
    }

    /**
     * Marks a specific initialization attempt as successful.
     *
     * @param initialization initialization handle returned by {@link #begin(UUID)}.
     * @param identity       immutable identity snapshot for the initialized player.
     */
    public void complete(PlayerIdentityInitialization initialization, PlayerIdentity identity) {
        if (initialization == null || identity == null || !initialization.belongsTo(identity.uuid())) {
            return;
        }
        completeIfCurrent(initialization, () -> initialization.complete(Optional.of(identity)));
    }

    /**
     * Marks a specific initialization attempt as unavailable, usually because the player disconnected early.
     *
     * @param initialization initialization handle returned by {@link #begin(UUID)}.
     */
    public void completeUnavailable(PlayerIdentityInitialization initialization) {
        if (initialization == null) {
            return;
        }
        completeIfCurrent(initialization, initialization::completeUnavailable);
    }

    /**
     * Marks a specific initialization attempt as failed.
     *
     * @param initialization initialization handle returned by {@link #begin(UUID)}.
     * @param failure        failure cause exposed to waiters.
     */
    public void fail(PlayerIdentityInitialization initialization, Throwable failure) {
        if (initialization == null) {
            return;
        }
        Throwable cause = failure == null
                ? new IllegalStateException("Player identity initialization failed.")
                : failure;
        completeIfCurrent(initialization, () -> initialization.completeExceptionally(cause));
    }

    /**
     * Cancels outstanding initialization waiters during plugin shutdown.
     */
    public void shutdown() {
        closed.set(true);
        CancellationException cancellation = new CancellationException("DataRegistry is shutting down.");
        pendingInitializations.forEach((uuid, initialization) -> initialization.completeExceptionally(cancellation));
        pendingInitializations.clear();
    }

    private void completeIfCurrent(PlayerIdentityInitialization initialization, Runnable completion) {
        if (!initialization.tracked()) {
            initialization.completeUnavailable();
            return;
        }
        pendingInitializations.computeIfPresent(initialization.uuid().toString(), (key, current) -> {
            if (current != initialization) {
                return current;
            }
            completion.run();
            return null;
        });
    }

    /**
     * Identity initialization handle for one concrete join/login attempt.
     */
    public static final class PlayerIdentityInitialization {

        private final UUID uuid;
        private final CompletableFuture<Optional<PlayerIdentity>> completion;
        private final boolean tracked;

        private PlayerIdentityInitialization(
                UUID uuid,
                CompletableFuture<Optional<PlayerIdentity>> completion,
                boolean tracked
        ) {
            this.uuid = uuid;
            this.completion = completion;
            this.tracked = tracked;
        }

        private static PlayerIdentityInitialization pending(UUID uuid) {
            return new PlayerIdentityInitialization(uuid, new CompletableFuture<>(), true);
        }

        private static PlayerIdentityInitialization untracked(UUID uuid) {
            return new PlayerIdentityInitialization(
                    uuid,
                    CompletableFuture.completedFuture(Optional.empty()),
                    false
            );
        }

        /**
         * Returns the UUID associated with this initialization attempt.
         */
        public UUID uuid() {
            return uuid;
        }

        /**
         * Returns a defensive copy of this initialization future.
         */
        public CompletableFuture<Optional<PlayerIdentity>> future() {
            return completion.copy();
        }

        private boolean tracked() {
            return tracked;
        }

        private boolean belongsTo(UUID playerUuid) {
            return uuid != null && uuid.equals(playerUuid);
        }

        private void complete(Optional<PlayerIdentity> identity) {
            completion.complete(identity);
        }

        private void completeUnavailable() {
            completion.complete(Optional.empty());
        }

        private void completeExceptionally(Throwable throwable) {
            completion.completeExceptionally(throwable);
        }
    }
}
