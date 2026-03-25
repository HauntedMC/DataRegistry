package nl.hauntedmc.dataregistry.platform.common.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;

import java.util.Objects;

public class PlayerStatusService {

    private static final int MAX_SERVER_LENGTH = 64;

    private final PlatformPlugin plugin;

    public PlayerStatusService(PlatformPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
    }

    /**
     * When a player joins, update or create the online status record.
     *
     * @param playerEntity  The persistent PlayerEntity (with generated ID).
     * @param currentServer The name of the server the player is joining.
     */
    public void updateStatus(PlayerEntity playerEntity, String currentServer) {
        if (playerEntity == null || playerEntity.getId() == null) {
            plugin.getPlatformLogger().warn("updateStatus called with invalid PlayerEntity.");
            return;
        }

        final String server = sanitizeServer(currentServer);

        try {
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                final PlayerEntity managed = session.merge(playerEntity);
                PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
                if (status == null) {
                    status = new PlayerOnlineStatusEntity();
                    status.setPlayer(managed);
                    status.setOnline(true);
                    status.setCurrentServer(server);
                    session.persist(status);
                } else {
                    status.setOnline(true);
                    status.setPreviousServer(status.getCurrentServer());
                    status.setCurrentServer(server);
                }
                return null;
            });
        } catch (Exception ex) {
            plugin.getPlatformLogger().error("Failed to update status for uuid=" + playerEntity.getUuid(), ex);
        }
    }

    /**
     * When a player quits, update their online status record.
     *
     * @param playerEntity  The persistent PlayerEntity.
     */
    public void updateStatusOnQuit(PlayerEntity playerEntity) {
        if (playerEntity == null || playerEntity.getId() == null) {
            plugin.getPlatformLogger().warn("updateStatusOnQuit called with invalid PlayerEntity.");
            return;
        }

        try {
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                final PlayerEntity managed = session.merge(playerEntity);
                PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
                if (status != null) {
                    status.setOnline(false);
                    status.setPreviousServer(status.getCurrentServer());
                    status.setCurrentServer("");
                }
                return null;
            });
        } catch (Exception ex) {
            plugin.getPlatformLogger().error("Failed to update quit status for uuid=" + playerEntity.getUuid(), ex);
        }
    }

    private static String sanitizeServer(String serverName) {
        if (serverName == null) {
            return "";
        }
        String value = serverName.trim();
        if (value.isEmpty()) {
            return "";
        }
        return value.length() <= MAX_SERVER_LENGTH ? value : value.substring(0, MAX_SERVER_LENGTH);
    }
}
