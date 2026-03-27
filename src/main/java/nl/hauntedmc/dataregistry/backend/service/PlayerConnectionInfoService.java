package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.time.Instant;
import java.util.Objects;

/**
 * Backend service for connection metadata updates.
 */
public final class PlayerConnectionInfoService {

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final boolean persistIpAddress;
    private final boolean persistVirtualHost;
    private final int ipAddressMaxLength;
    private final int virtualHostMaxLength;

    public PlayerConnectionInfoService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            boolean persistIpAddress,
            boolean persistVirtualHost,
            int ipAddressMaxLength,
            int virtualHostMaxLength
    ) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.persistIpAddress = persistIpAddress;
        this.persistVirtualHost = persistVirtualHost;
        if (ipAddressMaxLength < 7 || ipAddressMaxLength > 45) {
            throw new IllegalArgumentException("ipAddressMaxLength must be between 7 and 45.");
        }
        if (virtualHostMaxLength < 1 || virtualHostMaxLength > 255) {
            throw new IllegalArgumentException("virtualHostMaxLength must be between 1 and 255.");
        }
        this.ipAddressMaxLength = ipAddressMaxLength;
        this.virtualHostMaxLength = virtualHostMaxLength;
    }

    /**
     * Updates or creates connection info on player login.
     */
    public void updateOnLogin(PlayerEntity playerEntity, String ipAddress, String virtualHost) {
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("updateOnLogin called with an invalid player entity.");
            return;
        }

        final Instant now = Instant.now();
        final String sanitizedIp = persistIpAddress
                ? Sanitization.trimToLengthOrNull(ipAddress, ipAddressMaxLength)
                : null;
        final String sanitizedVirtualHost = persistVirtualHost
                ? Sanitization.trimToLengthOrNull(virtualHost, virtualHostMaxLength)
                : null;

        try {
            dataRegistry.getORM().runInTransaction(session -> {
                PlayerEntity managed = session.merge(playerEntity);
                PlayerConnectionInfoEntity info = session.find(PlayerConnectionInfoEntity.class, managed.getId());
                if (info == null) {
                    info = new PlayerConnectionInfoEntity();
                    info.setPlayer(managed);
                    info.setFirstConnectionAt(now);
                    info.setLastConnectionAt(now);
                    info.setIpAddress(sanitizedIp);
                    info.setVirtualHost(sanitizedVirtualHost);
                    session.persist(info);
                    return null;
                }

                if (info.getFirstConnectionAt() == null) {
                    info.setFirstConnectionAt(now);
                }
                info.setLastConnectionAt(now);
                info.setIpAddress(sanitizedIp);
                info.setVirtualHost(sanitizedVirtualHost);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update connection info for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    /**
     * Updates or creates disconnect timestamp for a player.
     */
    public void updateOnDisconnect(PlayerEntity playerEntity) {
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("updateOnDisconnect called with an invalid player entity.");
            return;
        }

        final Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                PlayerEntity managed = session.merge(playerEntity);
                PlayerConnectionInfoEntity info = session.find(PlayerConnectionInfoEntity.class, managed.getId());
                if (info == null) {
                    info = new PlayerConnectionInfoEntity();
                    info.setPlayer(managed);
                    info.setLastDisconnectAt(now);
                    session.persist(info);
                    return null;
                }

                info.setLastDisconnectAt(now);
                if (!persistIpAddress) {
                    info.setIpAddress(null);
                }
                if (!persistVirtualHost) {
                    info.setVirtualHost(null);
                }
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update disconnect info for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    private static boolean isPersistedPlayer(PlayerEntity playerEntity) {
        return playerEntity != null && playerEntity.getId() != null;
    }
}
