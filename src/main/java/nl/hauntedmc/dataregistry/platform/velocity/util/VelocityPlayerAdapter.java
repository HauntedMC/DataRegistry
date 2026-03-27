package nl.hauntedmc.dataregistry.platform.velocity.util;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

public class VelocityPlayerAdapter {

    private static volatile ProxyServer proxy;

    private VelocityPlayerAdapter() {
    }

    /**
     * Sets the ProxyServer instance for Velocity.
     * This must be called during plugin initialization.
     *
     * @param proxyServer the Velocity ProxyServer instance.
     */
    public static void setProxy(ProxyServer proxyServer) {
        proxy = Objects.requireNonNull(proxyServer, "proxyServer must not be null");
    }

    /**
     * Converts a Velocity Player into a PlayerEntity.
     *
     * @param player the Velocity player instance.
     * @return a new PlayerEntity populated with the player's data.
     */
    public static PlayerEntity fromPlatformPlayer(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        return fromSnapshot(player.getUniqueId().toString(), player.getUsername());
    }

    /**
     * Converts a captured Velocity player snapshot into a PlayerEntity.
     * This can be used safely from async code because it does not access Velocity API objects.
     */
    public static PlayerEntity fromSnapshot(String uuid, String username) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        Objects.requireNonNull(username, "username must not be null");
        PlayerEntity entity = new PlayerEntity();
        entity.setUuid(uuid);
        entity.setUsername(username);
        return entity;
    }

    /**
     * Retrieves a Velocity Player from the given PlayerEntity.
     *
     * @param entity the PlayerEntity with the player's UUID.
     * @return the Velocity Player instance if online, or null otherwise.
     */
    public static Player toPlatformPlayer(PlayerEntity entity) {
        if (entity == null || entity.getUuid() == null) {
            return null;
        }
        if (proxy == null) {
            throw new IllegalStateException(
                    "ProxyServer not set in VelocityPlayerAdapter. Call setProxy() during initialization."
            );
        }
        try {
            UUID uuid = UUID.fromString(entity.getUuid());
            Optional<Player> player = proxy.getPlayer(uuid);
            return player.orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
