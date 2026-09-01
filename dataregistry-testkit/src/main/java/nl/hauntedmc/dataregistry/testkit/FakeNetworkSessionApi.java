package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.session.NetworkSession;
import nl.hauntedmc.dataregistry.api.session.NetworkSessionApi;
import nl.hauntedmc.dataregistry.api.session.SessionDirectoryHealth;
import nl.hauntedmc.dataregistry.api.session.SessionDirectorySnapshot;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory session directory for feature contract and multi-node behavior tests. */
public final class FakeNetworkSessionApi implements NetworkSessionApi {
    private final Map<UUID, NetworkSession> sessions = new ConcurrentHashMap<>();

    public void put(NetworkSession session) {
        sessions.put(session.playerUuid(), session);
    }

    public void remove(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    @Override
    public SessionDirectoryHealth health() {
        Instant now = Instant.now();
        return new SessionDirectoryHealth(true, true, true, now, Optional.of(now), Optional.empty());
    }

    @Override
    public Optional<NetworkSession> cached(UUID playerUuid) {
        return Optional.ofNullable(sessions.get(playerUuid));
    }

    @Override
    public CompletionStage<Optional<NetworkSession>> find(UUID playerUuid) {
        return CompletableFuture.completedFuture(cached(playerUuid));
    }

    @Override
    public CompletionStage<Optional<NetworkSession>> findByUsername(String username) {
        return CompletableFuture.completedFuture(sessions.values().stream()
                .filter(session -> session.username().equalsIgnoreCase(username))
                .findFirst());
    }

    @Override
    public CompletionStage<SessionDirectorySnapshot> snapshot() {
        return CompletableFuture.completedFuture(new SessionDirectorySnapshot(Instant.now(), sessions));
    }

}
