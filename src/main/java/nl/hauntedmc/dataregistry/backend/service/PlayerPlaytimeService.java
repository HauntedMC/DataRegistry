package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeSegmentCloseReason;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.backend.playtime.PlaytimeGamemodeResolver;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend service for player playtime accrual and segment lifecycle updates.
 */
public final class PlayerPlaytimeService {

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final PlaytimeGamemodeResolver gamemodeResolver;
    private final int serverNameMaxLength;
    private final boolean featureEnabled;

    public PlayerPlaytimeService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            PlaytimeGamemodeResolver gamemodeResolver,
            int serverNameMaxLength
    ) {
        this(dataRegistry, logger, gamemodeResolver, serverNameMaxLength, true);
    }

    public PlayerPlaytimeService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            PlaytimeGamemodeResolver gamemodeResolver,
            int serverNameMaxLength,
            boolean featureEnabled
    ) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.gamemodeResolver = Objects.requireNonNull(gamemodeResolver, "gamemodeResolver must not be null");
        if (serverNameMaxLength < 1 || serverNameMaxLength > 64) {
            throw new IllegalArgumentException("serverNameMaxLength must be between 1 and 64.");
        }
        this.serverNameMaxLength = serverNameMaxLength;
        this.featureEnabled = featureEnabled;
    }

    public void onServerSwitch(PlayerEntity playerEntity, String serverName) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("onServerSwitch called with an invalid player entity.");
            return;
        }

        String sanitizedServerName = Sanitization.trimToLengthOrNull(serverName, serverNameMaxLength);
        if (sanitizedServerName == null) {
            return;
        }

        PlaytimeGamemodeResolver.ResolvedGamemode resolvedGamemode = gamemodeResolver.resolve(sanitizedServerName);
        Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                PlayerEntity managedPlayer = session.merge(playerEntity);
                Optional<PlayerSessionEntity> openSession = findOpenSession(session, managedPlayer.getId());
                Optional<PlayerPlaytimeSegmentEntity> openSegment = findOpenSegment(session, managedPlayer.getId());

                if (openSegment.isPresent()
                        && !belongsToOpenSession(openSegment.get(), openSession.orElse(null))) {
                    recoverStaleSegment(openSegment.get());
                    openSegment = Optional.empty();
                }

                if (openSegment.isPresent()) {
                    PlayerPlaytimeSegmentEntity currentSegment = openSegment.get();
                    if (!resolvedGamemode.tracked()) {
                        flushAndCloseSegment(
                                currentSegment,
                                now,
                                PlayerPlaytimeSegmentCloseReason.STOP_TRACKING,
                                session
                        );
                        return null;
                    }
                    if (Objects.equals(currentSegment.getGamemodeKey(), resolvedGamemode.gamemodeKey())) {
                        flushSegment(currentSegment, now, session);
                        currentSegment.setLastServer(resolvedGamemode.serverName());
                        return null;
                    }
                    flushAndCloseSegment(
                            currentSegment,
                            now,
                            PlayerPlaytimeSegmentCloseReason.SERVER_SWITCH,
                            session
                    );
                }

                if (resolvedGamemode.tracked() && openSession.isPresent()) {
                    openSegment(
                            managedPlayer,
                            openSession.get(),
                            resolvedGamemode.gamemodeKey(),
                            resolvedGamemode.serverName(),
                            now,
                            session
                    );
                }
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update playtime for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    public void flushActivePlaytime(PlayerEntity playerEntity) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("flushActivePlaytime called with an invalid player entity.");
            return;
        }

        Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                Optional<PlayerPlaytimeSegmentEntity> openSegment = findOpenSegment(session, playerEntity.getId());
                if (openSegment.isEmpty()) {
                    return null;
                }

                Optional<PlayerSessionEntity> openSession = findOpenSession(session, playerEntity.getId());
                PlayerPlaytimeSegmentEntity segment = openSegment.get();
                if (!belongsToOpenSession(segment, openSession.orElse(null))) {
                    recoverStaleSegment(segment);
                    return null;
                }

                flushSegment(segment, now, session);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to flush playtime for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    public void closeActivePlaytimeOnDisconnect(PlayerEntity playerEntity) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("closeActivePlaytimeOnDisconnect called with an invalid player entity.");
            return;
        }

        Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                Optional<PlayerPlaytimeSegmentEntity> openSegment = findOpenSegment(session, playerEntity.getId());
                if (openSegment.isEmpty()) {
                    return null;
                }

                Optional<PlayerSessionEntity> openSession = findOpenSession(session, playerEntity.getId());
                PlayerPlaytimeSegmentEntity segment = openSegment.get();
                if (!belongsToOpenSession(segment, openSession.orElse(null))) {
                    recoverStaleSegment(segment);
                    return null;
                }

                flushAndCloseSegment(
                        segment,
                        now,
                        PlayerPlaytimeSegmentCloseReason.DISCONNECT,
                        session
                );
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to close playtime for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    /**
     * Closes any segments left open by an unclean proxy shutdown before this runtime started.
     */
    public int recoverOpenSegmentsOnStartup() {
        if (!featureEnabled) {
            return 0;
        }
        try {
            return dataRegistry.getORM().runInTransaction(session -> {
                List<PlayerPlaytimeSegmentEntity> openSegments = session.createQuery(
                                "SELECT s FROM PlayerPlaytimeSegmentEntity s WHERE s.endedAt IS NULL",
                                PlayerPlaytimeSegmentEntity.class
                        )
                        .list();
                for (PlayerPlaytimeSegmentEntity openSegment : openSegments) {
                    recoverStaleSegment(openSegment);
                }
                return openSegments.size();
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to recover stale playtime segments during startup.", exception);
            return 0;
        }
    }

    private static Optional<PlayerSessionEntity> findOpenSession(Session session, Long playerId) {
        return session.createQuery(
                        "SELECT s FROM PlayerSessionEntity s " +
                                "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                "ORDER BY s.startedAt DESC, s.id DESC",
                        PlayerSessionEntity.class
                )
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    private static Optional<PlayerPlaytimeSegmentEntity> findOpenSegment(Session session, Long playerId) {
        return session.createQuery(
                        "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                "ORDER BY s.startedAt DESC, s.id DESC",
                        PlayerPlaytimeSegmentEntity.class
                )
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    private static boolean belongsToOpenSession(
            PlayerPlaytimeSegmentEntity segment,
            PlayerSessionEntity openSession
    ) {
        return segment.getSession() != null
                && openSession != null
                && Objects.equals(segment.getSession().getId(), openSession.getId());
    }

    private void openSegment(
            PlayerEntity player,
            PlayerSessionEntity sessionEntity,
            String gamemodeKey,
            String serverName,
            Instant now,
            Session session
    ) {
        PlayerPlaytimeEntity aggregate = findOrCreateAggregate(session, player, gamemodeKey, now);
        aggregate.setSegmentCount(Math.addExact(aggregate.getSegmentCount(), 1L));
        aggregate.setLastTrackedAt(now);

        PlayerPlaytimeSegmentEntity segment = new PlayerPlaytimeSegmentEntity();
        segment.setPlayer(player);
        segment.setSession(sessionEntity);
        segment.setGamemodeKey(gamemodeKey);
        segment.setEntryServer(serverName);
        segment.setLastServer(serverName);
        segment.setStartedAt(now);
        segment.setLastAccruedAt(now);
        session.persist(segment);
    }

    private void flushAndCloseSegment(
            PlayerPlaytimeSegmentEntity segment,
            Instant now,
            PlayerPlaytimeSegmentCloseReason closeReason,
            Session session
    ) {
        flushSegment(segment, now, session);
        segment.setEndedAt(maxInstant(now, segment.getStartedAt()));
        segment.setCloseReason(closeReason);
    }

    private void flushSegment(
            PlayerPlaytimeSegmentEntity segment,
            Instant now,
            Session session
    ) {
        Instant effectiveLastAccruedAt = maxInstant(segment.getLastAccruedAt(), segment.getStartedAt());
        if (effectiveLastAccruedAt == null || now.isBefore(effectiveLastAccruedAt)) {
            return;
        }

        long deltaMillis = Math.max(0L, Duration.between(effectiveLastAccruedAt, now).toMillis());
        if (deltaMillis > 0L) {
            PlayerPlaytimeEntity aggregate = findOrCreateAggregate(
                    session,
                    segment.getPlayer(),
                    segment.getGamemodeKey(),
                    segment.getStartedAt()
            );
            aggregate.setTrackedMillis(Math.addExact(aggregate.getTrackedMillis(), deltaMillis));
            aggregate.setLastTrackedAt(now);
            segment.setLastAccruedAt(now);
            return;
        }

        if (segment.getLastAccruedAt() == null) {
            segment.setLastAccruedAt(now);
        }
    }

    private PlayerPlaytimeEntity findOrCreateAggregate(
            Session session,
            PlayerEntity player,
            String gamemodeKey,
            Instant anchorTime
    ) {
        Optional<PlayerPlaytimeEntity> existing = session.createQuery(
                        "SELECT p FROM PlayerPlaytimeEntity p " +
                                "WHERE p.player.id = :playerId AND p.gamemodeKey = :gamemodeKey",
                        PlayerPlaytimeEntity.class
                )
                .setParameter("playerId", player.getId())
                .setParameter("gamemodeKey", gamemodeKey)
                .setMaxResults(1)
                .uniqueResultOptional();
        if (existing.isPresent()) {
            return existing.get();
        }

        PlayerPlaytimeEntity aggregate = new PlayerPlaytimeEntity();
        aggregate.setPlayer(player);
        aggregate.setGamemodeKey(gamemodeKey);
        aggregate.setTrackedMillis(0L);
        aggregate.setSegmentCount(0L);
        aggregate.setFirstTrackedAt(anchorTime);
        aggregate.setLastTrackedAt(anchorTime);
        session.persist(aggregate);
        return aggregate;
    }

    private static void recoverStaleSegment(PlayerPlaytimeSegmentEntity segment) {
        Instant recoveredEndTime = maxInstant(segment.getLastAccruedAt(), segment.getStartedAt());
        segment.setEndedAt(recoveredEndTime);
        segment.setCloseReason(PlayerPlaytimeSegmentCloseReason.RECOVERY);
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

    private static boolean isPersistedPlayer(PlayerEntity playerEntity) {
        return playerEntity != null && playerEntity.getId() != null;
    }
}
