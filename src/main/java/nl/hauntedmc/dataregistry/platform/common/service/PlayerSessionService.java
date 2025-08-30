package nl.hauntedmc.dataregistry.platform.common.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerSessionRepository;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;

import java.time.Instant;
import java.util.Optional;

public class PlayerSessionService {

    private static final int MAX_HOST_LEN = 255;

    private final PlatformPlugin plugin;
    private final PlayerSessionRepository repo;

    public PlayerSessionService(PlatformPlugin plugin) {
        this.plugin = plugin;
        this.repo = new PlayerSessionRepository(plugin.getDataRegistry().getORM());
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
        final String ip = safe(ipAddress, 45);
        final String vhost = safe(virtualHost, MAX_HOST_LEN);

        try {
            // Close any dangling session(s)
            repo.closeAllOpenSessions(playerEntity.getId(), now);

            // Create the new session
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                PlayerEntity managed = session.merge(playerEntity);
                PlayerSessionEntity sessionRow = new PlayerSessionEntity();
                sessionRow.setPlayer(managed);
                sessionRow.setIpAddress(ip);
                sessionRow.setVirtualHost(vhost);
                sessionRow.setStartedAt(now);
                session.persist(sessionRow);
                return null;
            });

            plugin.getPlatformLogger().info("Opened session for " + playerEntity.getUsername() +
                    " (" + playerEntity.getUuid() + ") ip=" + (ip == null ? "" : ip) +
                    " vhost=" + (vhost == null ? "" : vhost));
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
        final String name = safe(serverName, 64);
        if (name == null || name.isBlank()) return;

        try {
            plugin.getDataRegistry().getORM().runInTransaction(session -> {
                Optional<PlayerSessionEntity> open = repo.findOpenSessionForPlayer(playerEntity.getId());
                if (open.isEmpty()) {
                    return null; // No open session (can happen if login not yet processed)
                }
                PlayerSessionEntity s = session.merge(open.get());
                if (s.getFirstServer() == null || s.getFirstServer().isBlank()) {
                    s.setFirstServer(name);
                }
                s.setLastServer(name);
                session.merge(s);
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
                Optional<PlayerSessionEntity> open = repo.findOpenSessionForPlayer(playerEntity.getId());
                if (open.isEmpty()) {
                    return null; // Already closed or never opened; nothing to do
                }
                PlayerSessionEntity s = session.merge(open.get());
                if (s.getEndedAt() == null || s.getEndedAt().isBefore(now)) {
                    s.setEndedAt(now);
                    session.merge(s);
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
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        return v.length() <= maxLen ? v : v.substring(0, maxLen);
    }
}
