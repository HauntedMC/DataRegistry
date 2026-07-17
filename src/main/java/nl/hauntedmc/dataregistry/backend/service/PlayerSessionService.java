package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionVisitEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;

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
    private final boolean featureEnabled;
    private final boolean sessionVisitsEnabled;

    public PlayerSessionService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            boolean persistIpAddress,
            boolean persistVirtualHost,
            int ipAddressMaxLength,
            int virtualHostMaxLength,
            int serverNameMaxLength
    ) {
        this(
                dataRegistry,
                logger,
                persistIpAddress,
                persistVirtualHost,
                ipAddressMaxLength,
                virtualHostMaxLength,
                serverNameMaxLength,
                true,
                true
        );
    }

    public PlayerSessionService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            boolean persistIpAddress,
            boolean persistVirtualHost,
            int ipAddressMaxLength,
            int virtualHostMaxLength,
            int serverNameMaxLength,
            boolean featureEnabled
    ) {
        this(
                dataRegistry,
                logger,
                persistIpAddress,
                persistVirtualHost,
                ipAddressMaxLength,
                virtualHostMaxLength,
                serverNameMaxLength,
                featureEnabled,
                true
        );
    }

    public PlayerSessionService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            boolean persistIpAddress,
            boolean persistVirtualHost,
            int ipAddressMaxLength,
            int virtualHostMaxLength,
            int serverNameMaxLength,
            boolean featureEnabled,
            boolean sessionVisitsEnabled
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
        if (serverNameMaxLength < 1 || serverNameMaxLength > 64) {
            throw new IllegalArgumentException("serverNameMaxLength must be between 1 and 64.");
        }
        this.ipAddressMaxLength = ipAddressMaxLength;
        this.virtualHostMaxLength = virtualHostMaxLength;
        this.serverNameMaxLength = serverNameMaxLength;
        this.featureEnabled = featureEnabled;
        this.sessionVisitsEnabled = sessionVisitsEnabled;
    }

    /**
     * Creates a new open session. Any dangling open sessions are closed first.
     */
    public void openSessionOnLogin(PlayerEntity playerEntity, String ipAddress, String virtualHost) {
        if (!featureEnabled) {
            return;
        }
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
                if (sessionVisitsEnabled) {
                    session.createMutationQuery(
                                    "UPDATE PlayerSessionVisitEntity v SET v.leftAt = :end " +
                                            "WHERE v.player.id = :playerId AND v.leftAt IS NULL"
                            )
                            .setParameter("playerId", managed.getId())
                            .setParameter("end", now)
                            .executeUpdate();
                }

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
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("updateServerOnSwitch called with an invalid player entity.");
            return;
        }

        final String sanitizedServer = Sanitization.trimToLengthOrNull(serverName, serverNameMaxLength);
        if (sanitizedServer == null) {
            return;
        }

        final Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                Optional<PlayerSessionEntity> openSession = session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC, s.id DESC",
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
                if (sessionVisitsEnabled) {
                    updateSessionVisitOnSwitch(sessionEntity, sanitizedServer, now, session);
                }
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
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("closeSessionOnDisconnect called with an invalid player entity.");
            return;
        }

        final Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                if (sessionVisitsEnabled) {
                    closeOpenVisit(playerEntity.getId(), now, session);
                }
                Optional<PlayerSessionEntity> openSession = session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC, s.id DESC",
                                PlayerSessionEntity.class)
                        .setParameter("playerId", playerEntity.getId())
                        .setMaxResults(1)
                        .uniqueResultOptional();

                openSession.ifPresent(sessionEntity -> sessionEntity.setEndedAt(now));
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

    private void updateSessionVisitOnSwitch(
            PlayerSessionEntity sessionEntity,
            String serverName,
            Instant now,
            Session session
    ) {
        Optional<PlayerSessionVisitEntity> openVisit = findOpenVisit(session, sessionEntity.getPlayer().getId());
        if (openVisit.isPresent()) {
            PlayerSessionVisitEntity currentVisit = openVisit.get();
            if (!belongsToSession(currentVisit, sessionEntity)) {
                currentVisit.setLeftAt(maxInstant(now, currentVisit.getEnteredAt()));
                openVisit = Optional.empty();
            }
        }

        if (openVisit.isPresent()) {
            PlayerSessionVisitEntity currentVisit = openVisit.get();
            if (serverName.equals(currentVisit.getServerName())) {
                return;
            }
            currentVisit.setLeftAt(maxInstant(now, currentVisit.getEnteredAt()));
        }

        PlayerSessionVisitEntity visit = new PlayerSessionVisitEntity();
        visit.setPlayer(sessionEntity.getPlayer());
        visit.setSession(sessionEntity);
        visit.setServerName(serverName);
        visit.setEnteredAt(now);
        session.persist(visit);
    }

    private static void closeOpenVisit(Long playerId, Instant now, Session session) {
        findOpenVisit(session, playerId)
                .ifPresent(visit -> visit.setLeftAt(maxInstant(now, visit.getEnteredAt())));
    }

    private static Optional<PlayerSessionVisitEntity> findOpenVisit(Session session, Long playerId) {
        return session.createQuery(
                        "SELECT v FROM PlayerSessionVisitEntity v " +
                                "WHERE v.player.id = :playerId AND v.leftAt IS NULL " +
                                "ORDER BY v.enteredAt DESC, v.id DESC",
                        PlayerSessionVisitEntity.class
                )
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    private static boolean belongsToSession(PlayerSessionVisitEntity visit, PlayerSessionEntity sessionEntity) {
        return visit.getSession() != null
                && sessionEntity != null
                && Objects.equals(visit.getSession().getId(), sessionEntity.getId());
    }

    private static Instant maxInstant(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }
}
