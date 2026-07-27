package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Immutable public-player fixtures for feature tests.
 */
public final class PlayerFixtures {

    private PlayerFixtures() {
    }

    public static PlayerIdentity identity(long playerId, String username) {
        return new PlayerIdentity(
                playerId,
                UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)),
                username
        );
    }
}
