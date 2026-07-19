package nl.hauntedmc.dataregistry.backend.lifecycle;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Coordinates player identity readiness between platform lifecycle handlers and read-side consumers.
 * <p>
 * This class is part of DataRegistry's internal lifecycle layer. Platform listeners use it through
 * {@code PlayerService}; downstream plugins should wait through {@code PlayerDirectory#whenReady(UUID)}.
 */
public final class PlayerIdentityReadiness {

    private final ConcurrentMap<String, CompletableFuture<Optional<PlayerIdentity>>> pendingIdentities =
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
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        Optional<PlayerIdentity> activeIdentity = activeIdentitySupplier.get();
        if (activeIdentity.isPresent()) {
            return CompletableFuture.completedFuture(activeIdentity);
        }

        CompletableFuture<Optional<PlayerIdentity>> pending = pendingIdentities.get(uuid.toString());
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
     * Starts readiness tracking for a platform join lifecycle.
     *
     * @param uuid player UUID being initialized.
     * @return a defensive future copy for tests and lifecycle diagnostics.
     */
    public CompletableFuture<Optional<PlayerIdentity>> begin(UUID uuid) {
        if (uuid == null || closed.get()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return pendingIdentities.compute(uuid.toString(), (key, existing) -> {
            if (existing == null || existing.isDone()) {
                return new CompletableFuture<>();
            }
            return existing;
        }).copy();
    }

    /**
     * Marks the player's canonical identity as ready.
     *
     * @param identity immutable identity snapshot for the initialized player.
     */
    public void complete(PlayerIdentity identity) {
        if (identity == null) {
            return;
        }
        CompletableFuture<Optional<PlayerIdentity>> pending = pendingIdentities.remove(identity.uuid().toString());
        if (pending != null) {
            pending.complete(Optional.of(identity));
        }
    }

    /**
     * Marks identity preparation as unavailable, usually because the player disconnected early.
     *
     * @param uuid player UUID whose identity is no longer expected.
     */
    public void completeUnavailable(UUID uuid) {
        if (uuid == null) {
            return;
        }
        CompletableFuture<Optional<PlayerIdentity>> pending = pendingIdentities.remove(uuid.toString());
        if (pending != null) {
            pending.complete(Optional.empty());
        }
    }

    /**
     * Marks identity preparation as failed.
     *
     * @param uuid    player UUID whose initialization failed.
     * @param failure failure cause exposed to waiters.
     */
    public void fail(UUID uuid, Throwable failure) {
        if (uuid == null) {
            return;
        }
        CompletableFuture<Optional<PlayerIdentity>> pending = pendingIdentities.remove(uuid.toString());
        if (pending != null) {
            pending.completeExceptionally(
                    failure == null ? new IllegalStateException("Player identity initialization failed.") : failure
            );
        }
    }

    /**
     * Cancels outstanding readiness waiters during plugin shutdown.
     */
    public void shutdown() {
        closed.set(true);
        CancellationException cancellation = new CancellationException("DataRegistry is shutting down.");
        pendingIdentities.forEach((uuid, future) -> future.completeExceptionally(cancellation));
        pendingIdentities.clear();
    }
}
