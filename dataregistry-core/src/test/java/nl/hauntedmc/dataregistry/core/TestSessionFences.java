package nl.hauntedmc.dataregistry.core;

import nl.hauntedmc.dataregistry.api.session.SessionFence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class TestSessionFences {
    private TestSessionFences() { }

    public static SessionFence current() {
        return new SessionFence("test-proxy", new UUID(0L, 1L), new UUID(0L, 2L), 1L, 1L);
    }

    public static SessionFence newer() {
        return new SessionFence("test-proxy-2", new UUID(0L, 3L), new UUID(0L, 4L), 2L, 2L);
    }

    /** Returns a stable but globally distinct test fence for one player's session generation. */
    public static SessionFence forPlayer(UUID playerUuid) {
        return forPlayer(playerUuid, 1L);
    }

    /** Returns a stable but globally distinct test fence for a player's numbered reconnect. */
    public static SessionFence forPlayer(UUID playerUuid, long generation) {
        UUID sessionId = UUID.nameUUIDFromBytes(
                ("test-session:" + playerUuid + ':' + generation).getBytes(StandardCharsets.UTF_8)
        );
        return new SessionFence("test-proxy", new UUID(0L, 1L), sessionId, generation, generation);
    }
}
