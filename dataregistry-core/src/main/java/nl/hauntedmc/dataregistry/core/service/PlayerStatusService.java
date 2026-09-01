package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.session.SessionFence;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;

import java.util.Objects;

/**
 * Backend service for online status lifecycle updates.
 */
public final class PlayerStatusService {

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final int serverNameMaxLength;
    private final boolean featureEnabled;

    public PlayerStatusService(DataRegistry dataRegistry, ILoggerAdapter logger, int serverNameMaxLength) {
        this(dataRegistry, logger, serverNameMaxLength, true);
    }

    public PlayerStatusService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            int serverNameMaxLength,
            boolean featureEnabled
    ) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        if (serverNameMaxLength < 1 || serverNameMaxLength > 64) {
            throw new IllegalArgumentException("serverNameMaxLength must be between 1 and 64.");
        }
        this.serverNameMaxLength = serverNameMaxLength;
        this.featureEnabled = featureEnabled;
    }

    /**
     * Upserts a player's online status and updates current/previous server fields.
     */
    public void updateStatus(PlayerEntity playerEntity, String currentServer, SessionFence fence) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("updateStatus called with an invalid player entity.");
            return;
        }

        final String sanitizedServer = Sanitization.emptyIfNull(
                Sanitization.trimToLengthOrNull(currentServer, serverNameMaxLength)
        );

        try {
            dataRegistry.getORM().runInTransaction(session -> {
                updateStatus(session, playerEntity, sanitizedServer, fence);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update player online status for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    /**
     * Marks a freshly authenticated proxy connection online before it reaches a backend.
     * <p>
     * Existing online rows keep their current backend. This matters during reconnect/recovery races because the
     * subsequent transfer still needs the previous backend to move logical-gamemode population correctly.
     */
    public void updateStatusOnLogin(Session session, PlayerEntity playerEntity, SessionFence fence) {
        if (!featureEnabled) {
            return;
        }
        Objects.requireNonNull(session, "session must not be null");
        if (!isPersistedPlayer(playerEntity)) {
            throw new IllegalArgumentException("playerEntity must be a persisted player.");
        }
        PlayerEntity managed = session.merge(playerEntity);
        PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
        if (status == null) {
            status = new PlayerOnlineStatusEntity();
            status.setPlayer(managed);
            status.setOnline(true);
            status.setCurrentServer("");
            status.setSessionFence(fence);
            session.persist(status);
            return;
        }
        if (!status.isOnline()) {
            status.setPreviousServer(status.getCurrentServer());
            status.setCurrentServer("");
        }
        status.setOnline(true);
        status.setSessionFence(fence);
    }

    /**
     * Marks a player as offline.
     */
    public void updateStatusOnQuit(PlayerEntity playerEntity, SessionFence fence) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("updateStatusOnQuit called with an invalid player entity.");
            return;
        }

        try {
            dataRegistry.getORM().runInTransaction(session -> {
                updateStatusOnQuit(session, playerEntity, fence);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update player quit status for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    /**
     * Upserts online status in the supplied transaction.
     */
    public void updateStatus(Session session, PlayerEntity playerEntity, String currentServer, SessionFence fence) {
        if (!featureEnabled) {
            return;
        }
        Objects.requireNonNull(session, "session must not be null");
        if (!isPersistedPlayer(playerEntity)) {
            throw new IllegalArgumentException("playerEntity must be a persisted player.");
        }
        final String sanitizedServer = Sanitization.emptyIfNull(
                Sanitization.trimToLengthOrNull(currentServer, serverNameMaxLength)
        );
        PlayerEntity managed = session.merge(playerEntity);
        PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
        if (status == null) {
            status = new PlayerOnlineStatusEntity();
            status.setPlayer(managed);
            status.setOnline(true);
            status.setCurrentServer(sanitizedServer);
            status.setSessionFence(fence);
            session.persist(status);
            return;
        }

        if (!status.matches(fence)) {
            throw new IllegalStateException("Player status session fence changed before transfer");
        }

        status.setOnline(true);
        status.setPreviousServer(status.getCurrentServer());
        status.setCurrentServer(sanitizedServer);
    }

    /**
     * Marks a player offline in the supplied transaction.
     */
    public void updateStatusOnQuit(Session session, PlayerEntity playerEntity, SessionFence fence) {
        if (!featureEnabled) {
            return;
        }
        Objects.requireNonNull(session, "session must not be null");
        if (!isPersistedPlayer(playerEntity)) {
            throw new IllegalArgumentException("playerEntity must be a persisted player.");
        }
        PlayerEntity managed = session.merge(playerEntity);
        PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, managed.getId());
        if (status == null) {
            return;
        }
        if (!status.matches(fence)) {
            throw new IllegalStateException("Player status session fence changed before disconnect");
        }
        status.setOnline(false);
        status.setPreviousServer(status.getCurrentServer());
        status.setCurrentServer("");
    }

    public boolean mayClaim(Session session, PlayerEntity playerEntity, SessionFence fence) {
        PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, playerEntity.getId());
        return status == null || status.matches(fence) || status.getNetworkFencingToken() < fence.fencingToken();
    }

    public boolean isCurrent(Session session, PlayerEntity playerEntity, SessionFence fence) {
        PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, playerEntity.getId());
        return status != null && status.isOnline() && status.matches(fence);
    }

    private static boolean isPersistedPlayer(PlayerEntity playerEntity) {
        return playerEntity != null && playerEntity.getId() != null;
    }
}
