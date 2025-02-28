package nl.hauntedmc.dataregistry.platform.bukkit.service;

import nl.hauntedmc.dataregistry.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.entities.PlayerEntity;
import java.util.Optional;

public class PlayerService {

    private final BukkitDataRegistry plugin;

    public PlayerService(BukkitDataRegistry plugin) {
        this.plugin = plugin;
    }

    /**
     * On player join, delegate to the repository to retrieve or create the persistent record,
     * update the username if necessary, and cache it.
     *
     * @param tempEntity a temporary PlayerEntity built from live data.
     * @return the persistent PlayerEntity (with generated ID).
     */
    public PlayerEntity onPlayerJoin(PlayerEntity tempEntity) {
        String uuid = tempEntity.getUuid();
        String username = tempEntity.getUsername();
        return plugin.getDataRegistry().getPlayerRepository().getOrCreateActivePlayer(uuid, username);
    }

    /**
     * On player quit, remove the player from the active cache.
     *
     * @param uuid the player's UUID.
     */
    public void onPlayerQuit(String uuid) {
        plugin.getDataRegistry().getPlayerRepository().removeActivePlayer(uuid);
    }

    /**
     * Retrieve an active player (if present) from the repository's cache.
     *
     * @param uuid the player's UUID.
     * @return an Optional containing the PlayerEntity.
     */
    public Optional<PlayerEntity> getActivePlayer(String uuid) {
        return plugin.getDataRegistry().getPlayerRepository().getActivePlayer(uuid);
    }
}
