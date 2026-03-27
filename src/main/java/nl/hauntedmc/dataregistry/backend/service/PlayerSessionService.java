package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend service for player session lifecycle updates.
 */
public final class PlayerSessionService {

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final boolean persistIpAddress;
    private final boolean persistVirtualHost;
    private final int ipAddressMaxLength;
    private final int virtualHostMaxLength;
    private final int serverNameMaxLength;

    public PlayerSessionService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            boolean persistIpAddress,
            boolean persistVirtualHost,
            int ipAddressMaxLength,
            int virtualHostMaxLength,
            int serverNameMaxLength
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
        if (serverNameMaxLength < 1 || serverNameMaxLength > 128) {
            throw new IllegalArgumentException("serverNameMaxLength must be between 1 and 128.");
        }
        this.ipAddressMaxLength = ipAddressMaxLength;
        this.virtualHostMaxLength = virtualHostMaxLength;
        this.serverNameMaxLength = serverNameMaxLength;
    }

    /**
     * Creates a new open session. Any dangling open sessions are closed first.
     */
    public void openSessionOnLogin(PlayerEntity playerEntity, String ipAddress, String virtualHost) {
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("openSessionOnLogin called with an invalid player entity.");
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

                session.createMutationQuery(
                                "UPDATE PlayerSessionEntity s SET s.endedAt = :end " +
                                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL")
                        .setParameter("playerId", managed.getId())
                        .setParameter("end", now)
                        .executeUpdate();

                PlayerSessionEntity sessionEntity = new PlayerSessionEntity();
                sessionEntity.setPlayer(managed);
                sessionEntity.setIpAddress(sanitizedIp);
                sessionEntity.setVirtualHost(sanitizedVirtualHost);
                sessionEntity.setStartedAt(now);
                session.persist(sessionEntity);
                return null;
            });

            logger.info("Opened session for " + Sanitization.safeForLog(playerEntity.getUsername()) +
                    " (" + Sanitization.safeForLog(playerEntity.getUuid()) + ")");
        } catch (RuntimeException exception) {
            logger.error("Failed to open session for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    /**
     * Updates open session server information on backend switch.
     */
    public void updateServerOnSwitch(PlayerEntity playerEntity, String serverName) {
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("updateServerOnSwitch called with an invalid player entity.");
            return;
        }

        final String sanitizedServer = Sanitization.trimToLengthOrNull(serverName, serverNameMaxLength);
        if (sanitizedServer == null) {
            return;
        }

        try {
            dataRegistry.getORM().runInTransaction(session -> {
                Optional<PlayerSessionEntity> openSession = session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC",
                                PlayerSessionEntity.class)
                        .setParameter("playerId", playerEntity.getId())
                        .setMaxResults(1)
                        .uniqueResultOptional();

                if (openSession.isEmpty()) {
                    return null;
                }

                PlayerSessionEntity sessionEntity = openSession.get();
                if (sessionEntity.getFirstServer() == null || sessionEntity.getFirstServer().isBlank()) {
                    sessionEntity.setFirstServer(sanitizedServer);
                }
                sessionEntity.setLastServer(sanitizedServer);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update session server for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    /**
     * Closes a currently open session on disconnect.
     */
    public void closeSessionOnDisconnect(PlayerEntity playerEntity) {
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("closeSessionOnDisconnect called with an invalid player entity.");
            return;
        }

        final Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                Optional<PlayerSessionEntity> openSession = session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC",
                                PlayerSessionEntity.class)
                        .setParameter("playerId", playerEntity.getId())
                        .setMaxResults(1)
                        .uniqueResultOptional();

                if (openSession.isEmpty()) {
                    return null;
                }
                PlayerSessionEntity sessionEntity = openSession.get();
                sessionEntity.setEndedAt(now);
                return null;
            });

            logger.info("Closed session for " + Sanitization.safeForLog(playerEntity.getUsername()) +
                    " (" + Sanitization.safeForLog(playerEntity.getUuid()) + ")");
        } catch (RuntimeException exception) {
            logger.error("Failed to close session for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    private static boolean isPersistedPlayer(PlayerEntity playerEntity) {
        return playerEntity != null && playerEntity.getId() != null;
    }
}
