package nl.hauntedmc.dataregistry.platform.common.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;

public class PlayerStatusService {

    private final PlatformPlugin plugin;

    public PlayerStatusService(PlatformPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * When a player joins, update or create the online status record.
     *
     * @param playerEntity  The persistent PlayerEntity (with generated ID).
     * @param currentServer The name of the server the player is joining.
     */
    public void updateStatus(PlayerEntity playerEntity, String currentServer) {
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
                status.setPreviousServer(status.getCurrentServer());
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
     */
    public void updateStatusOnQuit(PlayerEntity playerEntity) {
        plugin.getDataRegistry().getORM().runInTransaction(session -> {
            final PlayerEntity managed = session.merge(playerEntity);
            PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
            if (status != null) {
                status.setOnline(false);
                status.setPreviousServer(status.getCurrentServer());
                status.setCurrentServer("");
                session.merge(status);
            }
            return null;
        });
    }
}
