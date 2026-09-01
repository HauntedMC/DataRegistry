package nl.hauntedmc.dataregistry.api.session;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Read-side access to all currently leased network sessions. */
public interface SessionDirectory {
    Optional<NetworkSession> cached(UUID playerUuid);
    CompletionStage<Optional<NetworkSession>> find(UUID playerUuid);
    CompletionStage<Optional<NetworkSession>> findByUsername(String username);
    CompletionStage<SessionDirectorySnapshot> snapshot();
}
