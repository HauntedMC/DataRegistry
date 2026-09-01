package nl.hauntedmc.dataregistry.core.session;

import nl.hauntedmc.dataregistry.api.session.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Startup sentinel replaced by the mandatory distributed implementation on each platform. */
public final class UnavailableNetworkSessionApi implements NetworkSessionApi {
    @Override public SessionDirectoryHealth health() {
        return new SessionDirectoryHealth(false, false, false, Instant.now(), Optional.empty(),
                Optional.of("Session directory is not installed"));
    }
    @Override public Optional<NetworkSession> cached(UUID playerUuid) { return Optional.empty(); }
    @Override public CompletionStage<Optional<NetworkSession>> find(UUID playerUuid) { return failed(); }
    @Override public CompletionStage<Optional<NetworkSession>> findByUsername(String username) { return failed(); }
    @Override public CompletionStage<SessionDirectorySnapshot> snapshot() { return failed(); }

    private static <T> CompletableFuture<T> failed() {
        return CompletableFuture.failedFuture(new IllegalStateException("Session directory is not installed"));
    }
}
