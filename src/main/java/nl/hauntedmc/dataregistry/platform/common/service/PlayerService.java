package nl.hauntedmc.dataregistry.platform.common.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;

import java.util.Optional;

public class PlayerService {

    private final PlatformPlugin plugin;

    public PlayerService(PlatformPlugin plugin) {
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
        PlayerEntity player = plugin.getDataRegistry().getPlayerRepository().getOrCreateActivePlayer(uuid, username);
        plugin.getPlatformLogger().info("Added " + username + " ("+uuid+") to the local player repository.");
        return player;
    }

    /**
     * On player quit, remove the player from the active cache.
     *
     * @param uuid the player's UUID.
     */
    public void onPlayerQuit(String username, String uuid) {
        plugin.getDataRegistry().getPlayerRepository().removeActivePlayer(uuid);
        plugin.getPlatformLogger().info("Removed " + username + " ("+uuid+") from the local player repository.");
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
