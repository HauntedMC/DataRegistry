package nl.hauntedmc.dataregistry.platform.bukkit.util;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.Objects;

public class BukkitPlayerAdapter {

    private BukkitPlayerAdapter() {
    }

    /**
     * Converts a live Bukkit Player into a temporary PlayerEntity.
     * This instance does not have a generated ID until merged via PlayerService.
     */
    public static PlayerEntity fromPlatformPlayer(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        PlayerEntity entity = new PlayerEntity();
        entity.setUuid(player.getUniqueId().toString());
        entity.setUsername(player.getName());
        return entity;
    }

    /**
     * Retrieves a live Bukkit Player from a persistent PlayerEntity using its UUID.
     */
    public static Player toPlatformPlayer(PlayerEntity entity) {
        if (entity == null || entity.getUuid() == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(entity.getUuid());
            return Bukkit.getPlayer(uuid);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
