package nl.hauntedmc.dataregistry.platform.velocity.util;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Optional;
import java.util.UUID;

public class VelocityPlayerAdapter {

    private static ProxyServer proxy;

    /**
     * Sets the ProxyServer instance for Velocity.
     * This must be called during plugin initialization.
     *
     * @param proxyServer the Velocity ProxyServer instance.
     */
    public static void setProxy(ProxyServer proxyServer) {
        proxy = proxyServer;
    }

    /**
     * Converts a Velocity Player into a PlayerEntity.
     *
     * @param player the Velocity player instance.
     * @return a new PlayerEntity populated with the player's data.
     */
    public static PlayerEntity fromPlatformPlayer(Player player) {
        PlayerEntity entity = new PlayerEntity();
        entity.setUuid(player.getUniqueId().toString());
        entity.setUsername(player.getUsername());
        return entity;
    }

    /**
     * Retrieves a Velocity Player from the given PlayerEntity.
     *
     * @param entity the PlayerEntity with the player's UUID.
     * @return the Velocity Player instance if online, or null otherwise.
     */
    public static Player toPlatformPlayer(PlayerEntity entity) {
        if (proxy == null) {
            throw new IllegalStateException("ProxyServer not set in VelocityPlayerAdapter. Call setProxy() during initialization.");
        }
        UUID uuid = UUID.fromString(entity.getUuid());
        Optional<Player> player = proxy.getPlayer(uuid);
        return player.orElse(null);
    }
}
