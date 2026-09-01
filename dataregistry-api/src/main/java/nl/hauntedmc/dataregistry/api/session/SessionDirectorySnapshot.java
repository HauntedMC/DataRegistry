package nl.hauntedmc.dataregistry.api.session;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Point-in-time global live-session projection. */
public record SessionDirectorySnapshot(Instant capturedAt, Map<UUID, NetworkSession> sessions) {
    public SessionDirectorySnapshot {
        Objects.requireNonNull(capturedAt, "capturedAt");
        sessions = sessions == null ? Map.of() : Map.copyOf(sessions);
    }
}
