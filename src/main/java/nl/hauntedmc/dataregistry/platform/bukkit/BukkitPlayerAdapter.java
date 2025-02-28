package nl.hauntedmc.dataregistry.platform.bukkit;

import nl.hauntedmc.dataregistry.entities.PlayerEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public class BukkitPlayerAdapter {

    /**
     * Converts a live Bukkit Player into a temporary PlayerEntity.
     * This instance does not have a generated ID until merged via PlayerService.
     */
    public static PlayerEntity fromPlatformPlayer(Player player) {
        PlayerEntity entity = new PlayerEntity();
        entity.setUuid(player.getUniqueId().toString());
        entity.setUsername(player.getName());
        return entity;
    }

    /**
     * Retrieves a live Bukkit Player from a persistent PlayerEntity using its UUID.
     */
    public static Player toPlatformPlayer(PlayerEntity entity) {
        UUID uuid = UUID.fromString(entity.getUuid());
        return Bukkit.getPlayer(uuid);
    }
}
