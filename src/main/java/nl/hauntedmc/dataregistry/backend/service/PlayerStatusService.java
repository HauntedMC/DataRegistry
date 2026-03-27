package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.util.Objects;

/**
 * Backend service for online status lifecycle updates.
 */
public final class PlayerStatusService {

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final int serverNameMaxLength;

    public PlayerStatusService(DataRegistry dataRegistry, ILoggerAdapter logger, int serverNameMaxLength) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        if (serverNameMaxLength < 1 || serverNameMaxLength > 64) {
            throw new IllegalArgumentException("serverNameMaxLength must be between 1 and 64.");
        }
        this.serverNameMaxLength = serverNameMaxLength;
    }

    /**
     * Upserts a player's online status and updates current/previous server fields.
     */
    public void updateStatus(PlayerEntity playerEntity, String currentServer) {
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("updateStatus called with an invalid player entity.");
            return;
        }

        final String sanitizedServer = Sanitization.emptyIfNull(
                Sanitization.trimToLengthOrNull(currentServer, serverNameMaxLength)
        );

        try {
            dataRegistry.getORM().runInTransaction(session -> {
                PlayerEntity managed = session.merge(playerEntity);
                PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
                if (status == null) {
                    status = new PlayerOnlineStatusEntity();
                    status.setPlayer(managed);
                    status.setOnline(true);
                    status.setCurrentServer(sanitizedServer);
                    session.persist(status);
                    return null;
                }

                status.setOnline(true);
                status.setPreviousServer(status.getCurrentServer());
                status.setCurrentServer(sanitizedServer);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update player online status for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    /**
     * Marks a player as offline.
     */
    public void updateStatusOnQuit(PlayerEntity playerEntity) {
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("updateStatusOnQuit called with an invalid player entity.");
            return;
        }

        try {
            dataRegistry.getORM().runInTransaction(session -> {
                PlayerEntity managed = session.merge(playerEntity);
                PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
                if (status == null) {
                    return null;
                }
                status.setOnline(false);
                status.setPreviousServer(status.getCurrentServer());
                status.setCurrentServer("");
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update player quit status for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    private static boolean isPersistedPlayer(PlayerEntity playerEntity) {
        return playerEntity != null && playerEntity.getId() != null;
    }
}
