package nl.hauntedmc.dataregistry.platform.common.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class PlayerSessionService {

    private static final int MAX_IP_LEN = 45;
    private static final int MAX_HOST_LEN = 255;
    private static final int MAX_SERVER_LEN = 64;

    private final PlatformPlugin plugin;

    public PlayerSessionService(PlatformPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
    }

    /**
     * Create a new session on login. If an open session exists (e.g., previous disconnect missed),
     * close it first with the same timestamp to keep data consistent.
     */
    public void openSessionOnLogin(PlayerEntity playerEntity, String ipAddress, String virtualHost) {
        if (playerEntity == null || playerEntity.getId() == null) {
            plugin.getPlatformLogger().warn("openSessionOnLogin: playerEntity invalid.");
            return;
        }
        final Instant now = Instant.now();
        final String ip = safe(ipAddress, MAX_IP_LEN);
        final String vhost = safe(virtualHost, MAX_HOST_LEN);

        try {
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                PlayerEntity managed = session.merge(playerEntity);

                // Close dangling sessions first (e.g., if a disconnect event was missed).
                session.createMutationQuery(
                                "UPDATE PlayerSessionEntity s SET s.endedAt = :end " +
                                        "WHERE s.player.id = :pid AND s.endedAt IS NULL")
                        .setParameter("pid", managed.getId())
                        .setParameter("end", now)
                        .executeUpdate();

                PlayerSessionEntity sessionRow = new PlayerSessionEntity();
                sessionRow.setPlayer(managed);
                sessionRow.setIpAddress(ip);
                sessionRow.setVirtualHost(vhost);
                sessionRow.setStartedAt(now);
                session.persist(sessionRow);
                return null;
            });

            plugin.getPlatformLogger().info("Opened session for " + playerEntity.getUsername() +
                    " (" + playerEntity.getUuid() + ")");
        } catch (Exception ex) {
            plugin.getPlatformLogger().error("openSessionOnLogin failed for uuid=" + playerEntity.getUuid(), ex);
        }
    }

    /**
     * Update the current session's server info on backend switch.
     * - firstServer is set once (if null).
     * - lastServer is updated on each switch.
     */
    public void updateServerOnSwitch(PlayerEntity playerEntity, String serverName) {
        if (playerEntity == null || playerEntity.getId() == null) {
            plugin.getPlatformLogger().warn("updateServerOnSwitch: playerEntity invalid.");
            return;
        }
        final String name = safe(serverName, MAX_SERVER_LEN);
        if (name == null) {
            return;
        }

        try {
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                Optional<PlayerSessionEntity> open = session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :pid AND s.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC",
                                PlayerSessionEntity.class)
                        .setParameter("pid", playerEntity.getId())
                        .setMaxResults(1)
                        .uniqueResultOptional();
                if (open.isEmpty()) {
                    return null; // No open session (can happen if login not yet processed)
                }
                PlayerSessionEntity s = open.get();
                if (s.getFirstServer() == null || s.getFirstServer().isBlank()) {
                    s.setFirstServer(name);
                }
                s.setLastServer(name);
                return null;
            });
        } catch (Exception ex) {
            plugin.getPlatformLogger().error("updateServerOnSwitch failed for uuid=" + playerEntity.getUuid(), ex);
        }
    }

    /**
     * Close the player's current open session (if any) on disconnect.
     */
    public void closeSessionOnDisconnect(PlayerEntity playerEntity) {
        if (playerEntity == null || playerEntity.getId() == null) {
            plugin.getPlatformLogger().warn("closeSessionOnDisconnect: playerEntity invalid.");
            return;
        }
        final Instant now = Instant.now();

        try {
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                Optional<PlayerSessionEntity> open = session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :pid AND s.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC",
                                PlayerSessionEntity.class)
                        .setParameter("pid", playerEntity.getId())
                        .setMaxResults(1)
                        .uniqueResultOptional();
                if (open.isEmpty()) {
                    return null; // Already closed or never opened; nothing to do
                }
                PlayerSessionEntity s = open.get();
                if (s.getEndedAt() == null || s.getEndedAt().isBefore(now)) {
                    s.setEndedAt(now);
                }
                return null;
            });
            plugin.getPlatformLogger().info("Closed session for " + playerEntity.getUsername() +
                    " (" + playerEntity.getUuid() + ")");
        } catch (Exception ex) {
            plugin.getPlatformLogger().error("closeSessionOnDisconnect failed for uuid=" + playerEntity.getUuid(), ex);
        }
    }

    private static String safe(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        return v.length() <= maxLen ? v : v.substring(0, maxLen);
    }
}
