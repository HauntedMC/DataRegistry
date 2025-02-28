package nl.hauntedmc.dataregistry.platform.bukkit.service;

import nl.hauntedmc.dataregistry.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

public class PlayerStatusService {

    private final BukkitDataRegistry plugin;

    public PlayerStatusService(BukkitDataRegistry plugin) {
        this.plugin = plugin;
    }

    /**
     * When a player joins, update or create the online status record.
     *
     * @param playerEntity  The persistent PlayerEntity (with generated ID).
     * @param currentServer The name of the server the player is joining.
     */
    public void updateStatusOnJoin(PlayerEntity playerEntity, String currentServer) {
        plugin.getDataRegistry().getORM().runInTransaction(session -> {
            // Merge the playerEntity and store it in a new final variable.
            final PlayerEntity managed = session.merge(playerEntity);

            // Now, use the managed entity (which should have its ID generated).
            PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
            if (status == null) {
                status = new PlayerOnlineStatusEntity();
                status.setPlayer(managed);
                status.setOnline(true);
                status.setCurrentServer(currentServer);
                session.persist(status);
            } else {
                status.setOnline(true);
                status.setCurrentServer(currentServer);
                session.merge(status);
            }
            return null;
        });
    }

    /**
     * When a player quits, update their online status record.
     *
     * @param playerEntity  The persistent PlayerEntity.
     * @param currentServer The server name at quit time.
     */
    public void updateStatusOnQuit(PlayerEntity playerEntity, String currentServer) {
        plugin.getDataRegistry().getORM().runInTransaction(session -> {
            PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, playerEntity.getId());
            if (status != null) {
                status.setOnline(false);
                status.setPreviousServer(status.getCurrentServer());
                status.setCurrentServer(currentServer);
                session.merge(status);
            }
            return null;
        });
    }
}
