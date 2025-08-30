package nl.hauntedmc.dataregistry.platform.common.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;

import java.time.Instant;

public class PlayerConnectionInfoService {

    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_VHOST_LENGTH = 255;

    private final PlatformPlugin plugin;

    public PlayerConnectionInfoService(PlatformPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Update or create the player's connection info when they log in.
     * - Sets firstConnectionAt only once (if null).
     * - Updates lastConnectionAt every login.
     * - Updates IP address and virtual host when provided.
     */
    public void updateOnLogin(PlayerEntity playerEntity, String ipAddress, String virtualHost) {
        if (playerEntity == null) {
            plugin.getPlatformLogger().warn("updateOnLogin called with null PlayerEntity.");
            return;
        }

        final String ip = clampOrNull(safeTrim(ipAddress), MAX_IP_LENGTH);
        final String vhost = clampOrNull(safeTrim(virtualHost), MAX_VHOST_LENGTH);
        final Instant now = Instant.now();

        try {
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                final PlayerEntity managed = session.merge(playerEntity);
                if (managed.getId() == null) {
                    // Should not happen, but guard anyway
                    plugin.getPlatformLogger().warn("PlayerEntity merge returned null ID for uuid=" + managed.getUuid());
                    return null;
                }

                PlayerConnectionInfoEntity info = session.find(PlayerConnectionInfoEntity.class, managed.getId());
                if (info == null) {
                    info = new PlayerConnectionInfoEntity();
                    info.setPlayer(managed);
                    info.setFirstConnectionAt(now);
                    info.setLastConnectionAt(now);
                    info.setIpAddress(ip);
                    info.setVirtualHost(vhost);
                    session.persist(info);
                } else {
                    if (info.getFirstConnectionAt() == null) {
                        info.setFirstConnectionAt(now);
                    }
                    info.setLastConnectionAt(now);
                    if (ip != null && !ip.isBlank()) {
                        info.setIpAddress(ip);
                    }
                    if (vhost != null && !vhost.isBlank()) {
                        info.setVirtualHost(vhost);
                    }
                    session.merge(info);
                }
                return null;
            });
        } catch (Exception ex) {
            plugin.getPlatformLogger().error("Failed to update connection info on login for uuid=" +
                    playerEntity.getUuid(), ex);
        }
    }

    /**
     * Update (or create if missing) the player's connection info when they disconnect.
     * - Updates lastDisconnectAt.
     * - Does not change firstConnectionAt once set.
     */
    public void updateOnDisconnect(PlayerEntity playerEntity) {
        if (playerEntity == null) {
            plugin.getPlatformLogger().warn("updateOnDisconnect called with null PlayerEntity.");
            return;
        }

        final Instant now = Instant.now();

        try {
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                final PlayerEntity managed = session.merge(playerEntity);
                if (managed.getId() == null) {
                    plugin.getPlatformLogger().warn("PlayerEntity merge returned null ID on disconnect for uuid=" + managed.getUuid());
                    return null;
                }

                PlayerConnectionInfoEntity info = session.find(PlayerConnectionInfoEntity.class, managed.getId());
                if (info == null) {
                    info = new PlayerConnectionInfoEntity();
                    info.setPlayer(managed);
                    // Don't set first/last connection here since we missed login event; only record disconnect as requested.
                    info.setLastDisconnectAt(now);
                    session.persist(info);
                } else {
                    info.setLastDisconnectAt(now);
                    session.merge(info);
                }
                return null;
            });
        } catch (Exception ex) {
            plugin.getPlatformLogger().error("Failed to update connection info on disconnect for uuid=" +
                    playerEntity.getUuid(), ex);
        }
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private static String clampOrNull(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
