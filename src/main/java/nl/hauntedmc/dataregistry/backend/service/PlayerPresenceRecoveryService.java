package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.entities.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeSegmentCloseReason;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionVisitEntity;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reconciles player presence rows that can be left open by process crashes.
 * <p>
 * The service intentionally ends stale rows at the latest durable activity timestamp already stored before the crash.
 * It never uses startup time as a synthetic disconnect time, so recovered sessions cannot accrue offline duration.
 */
public final class PlayerPresenceRecoveryService {
    /**
     * Fallback for corrupt legacy rows that are open but have no usable start or activity timestamp.
     * Healthy rows never use this path; it avoids inventing uptime between a crash and the next startup.
     */
    private static final Instant UNKNOWN_RECOVERY_TIME = Instant.EPOCH;

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final DataRegistrySettings settings;

    public PlayerPresenceRecoveryService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            DataRegistrySettings settings
    ) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * Closes stale open presence rows left by an unclean previous shutdown.
     *
     * @return counts for each recovered state category.
     */
    public PlayerPresenceRecoveryResult recoverAfterUncleanShutdown() {
        if (!hasRecoverableFeatureEnabled()) {
            return PlayerPresenceRecoveryResult.empty();
        }

        try {
            return dataRegistry.getORM().runInTransaction(this::recoverInTransaction);
        } catch (RuntimeException exception) {
            logger.error("Failed to recover stale player presence state during startup.", exception);
            return PlayerPresenceRecoveryResult.empty();
        }
    }

    private PlayerPresenceRecoveryResult recoverInTransaction(Session session) {
        RecoveryState state = new RecoveryState();

        int segmentsClosed = recoverPlaytimeSegments(session, state);
        int sessionsClosed = recoverSessions(session, state);
        int visitsClosed = recoverSessionVisits(session, state);
        int statusesCleared = recoverOnlineStatuses(session, state);
        int summariesUpdated = recoverActivitySummaries(session, state);
        int connectionInfosUpdated = recoverConnectionInfos(session, state);

        return new PlayerPresenceRecoveryResult(
                segmentsClosed,
                sessionsClosed,
                visitsClosed,
                statusesCleared,
                summariesUpdated,
                connectionInfosUpdated
        );
    }

    private int recoverPlaytimeSegments(Session session, RecoveryState state) {
        if (!settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME)) {
            return 0;
        }

        List<PlayerPlaytimeSegmentEntity> openSegments = session.createQuery(
                        "SELECT s FROM PlayerPlaytimeSegmentEntity s WHERE s.endedAt IS NULL",
                        PlayerPlaytimeSegmentEntity.class
                )
                .list();
        for (PlayerPlaytimeSegmentEntity segment : openSegments) {
            Instant recoveredEndTime = recoveredSegmentEndTime(segment);
            segment.setEndedAt(recoveredEndTime);
            segment.setCloseReason(PlayerPlaytimeSegmentCloseReason.RECOVERY);
            state.rememberSessionActivity(segment.getSession(), recoveredEndTime);
            state.rememberPlayerActivity(segment.getPlayer(), recoveredEndTime);
        }
        return openSegments.size();
    }

    private int recoverSessions(Session session, RecoveryState state) {
        if (!settings.isFeatureEnabled(DataRegistryFeature.SESSIONS)) {
            return 0;
        }

        List<PlayerSessionEntity> openSessions = session.createQuery(
                        "SELECT s FROM PlayerSessionEntity s WHERE s.endedAt IS NULL",
                        PlayerSessionEntity.class
                )
                .list();
        Map<Long, Integer> openSessionCountsByPlayerId = countOpenSessionsByPlayerId(openSessions);
        for (PlayerSessionEntity openSession : openSessions) {
            state.rememberPlayer(openSession.getPlayer());
            boolean onlyOpenSessionForPlayer = openSessionCountsByPlayerId.getOrDefault(
                    playerId(openSession.getPlayer()),
                    0
            ) == 1;
            Instant recoveryEndTime = recoveredSessionEndTime(session, openSession, state, onlyOpenSessionForPlayer);
            openSession.setEndedAt(recoveryEndTime);
            state.rememberSessionActivity(openSession, recoveryEndTime);
            state.rememberPlayerActivity(openSession.getPlayer(), recoveryEndTime);
        }
        return openSessions.size();
    }

    private int recoverSessionVisits(Session session, RecoveryState state) {
        if (!settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS)) {
            return 0;
        }

        List<PlayerSessionVisitEntity> openVisits = session.createQuery(
                        "SELECT v FROM PlayerSessionVisitEntity v WHERE v.leftAt IS NULL",
                        PlayerSessionVisitEntity.class
                )
                .list();
        for (PlayerSessionVisitEntity openVisit : openVisits) {
            PlayerSessionEntity sessionEntity = openVisit.getSession();
            Instant recoveryEndTime = state.sessionActivity(sessionEntity);
            if (recoveryEndTime == null && sessionEntity != null) {
                recoveryEndTime = sessionEntity.getEndedAt();
            }
            recoveryEndTime = maxInstant(recoveryEndTime, openVisit.getEnteredAt());
            recoveryEndTime = fallbackRecoveryTime(recoveryEndTime);

            openVisit.setLeftAt(recoveryEndTime);
            state.rememberSessionActivity(sessionEntity, recoveryEndTime);
            state.rememberPlayerActivity(openVisit.getPlayer(), recoveryEndTime);
        }
        return openVisits.size();
    }

    private int recoverOnlineStatuses(Session session, RecoveryState state) {
        if (!settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)) {
            return 0;
        }

        List<PlayerOnlineStatusEntity> onlineStatuses = session.createQuery(
                        "SELECT s FROM PlayerOnlineStatusEntity s WHERE s.online = true",
                        PlayerOnlineStatusEntity.class
                )
                .list();
        for (PlayerOnlineStatusEntity onlineStatus : onlineStatuses) {
            PlayerEntity player = onlineStatus.getPlayer();
            Long playerId = resolvePlayerId(player, onlineStatus.getPlayerId());
            if (player != null) {
                state.rememberPlayer(player);
            }
            state.rememberPlayerActivity(playerId, latestKnownPresenceTime(session, playerId, player, state));

            onlineStatus.setOnline(false);
            onlineStatus.setPreviousServer(onlineStatus.getCurrentServer());
            onlineStatus.setCurrentServer("");
        }
        return onlineStatuses.size();
    }

    private int recoverActivitySummaries(Session session, RecoveryState state) {
        if (!settings.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY)) {
            return 0;
        }

        int updated = 0;
        for (Long playerId : state.recoveredPlayerIds()) {
            Instant recoveredAt = state.playerActivity(playerId);
            if (recoveredAt == null) {
                continue;
            }
            PlayerActivitySummaryEntity summary = session.find(PlayerActivitySummaryEntity.class, playerId);
            if (summary == null) {
                PlayerEntity player = state.player(playerId);
                if (player == null) {
                    continue;
                }
                summary = new PlayerActivitySummaryEntity();
                summary.setPlayer(player);
                summary.setFirstSeenAt(recoveredAt);
                summary.setLastSeenAt(recoveredAt);
                summary.setLastLogoutAt(recoveredAt);
                session.persist(summary);
                updated++;
                continue;
            }

            boolean changed = false;
            if (summary.getFirstSeenAt() == null) {
                summary.setFirstSeenAt(recoveredAt);
                changed = true;
            }
            if (isAfter(recoveredAt, summary.getLastSeenAt())) {
                summary.setLastSeenAt(recoveredAt);
                changed = true;
            }
            if (isAfter(recoveredAt, summary.getLastLogoutAt())) {
                summary.setLastLogoutAt(recoveredAt);
                changed = true;
            }
            if (changed) {
                updated++;
            }
        }
        return updated;
    }

    private int recoverConnectionInfos(Session session, RecoveryState state) {
        if (!settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)) {
            return 0;
        }

        int updated = 0;
        for (Long playerId : state.recoveredPlayerIds()) {
            Instant recoveredAt = state.playerActivity(playerId);
            if (recoveredAt == null) {
                continue;
            }
            PlayerConnectionInfoEntity info = session.find(PlayerConnectionInfoEntity.class, playerId);
            if (info == null) {
                PlayerEntity player = state.player(playerId);
                if (player == null) {
                    continue;
                }
                info = new PlayerConnectionInfoEntity();
                info.setPlayer(player);
                info.setLastDisconnectAt(recoveredAt);
                session.persist(info);
                updated++;
                continue;
            }

            boolean changed = false;
            if (isAfter(recoveredAt, info.getLastDisconnectAt())) {
                info.setLastDisconnectAt(recoveredAt);
                changed = true;
            }
            if (!settings.persistIpAddress() && info.getIpAddress() != null) {
                info.setIpAddress(null);
                changed = true;
            }
            if (!settings.persistVirtualHost() && info.getVirtualHost() != null) {
                info.setVirtualHost(null);
                changed = true;
            }
            if (changed) {
                updated++;
            }
        }
        return updated;
    }

    private Instant recoveredSessionEndTime(
            Session session,
            PlayerSessionEntity playerSession,
            RecoveryState state,
            boolean includePlayerLevelActivity
    ) {
        Long playerId = playerId(playerSession.getPlayer());
        Instant recoveredAt = state.sessionActivity(playerSession);
        recoveredAt = maxInstant(recoveredAt, playerSession.getStartedAt());
        if (includePlayerLevelActivity) {
            recoveredAt = maxInstant(
                    recoveredAt,
                    latestKnownPresenceTime(session, playerId, playerSession.getPlayer(), state)
            );
        }
        return fallbackRecoveryTime(recoveredAt);
    }

    private Instant latestKnownPresenceTime(
            Session session,
            Long playerId,
            PlayerEntity player,
            RecoveryState state
    ) {
        if (playerId == null) {
            return null;
        }

        state.rememberPlayer(player);
        Instant latest = state.playerActivity(playerId);
        if (settings.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY)) {
            PlayerActivitySummaryEntity summary = session.find(PlayerActivitySummaryEntity.class, playerId);
            if (summary != null) {
                latest = maxInstant(latest, summary.getLastSeenAt());
                latest = maxInstant(latest, summary.getLastLoginAt());
                latest = maxInstant(latest, summary.getLastLogoutAt());
            }
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)) {
            PlayerConnectionInfoEntity connectionInfo = session.find(PlayerConnectionInfoEntity.class, playerId);
            if (connectionInfo != null) {
                latest = maxInstant(latest, connectionInfo.getLastConnectionAt());
                latest = maxInstant(latest, connectionInfo.getLastDisconnectAt());
            }
        }
        return latest;
    }

    private boolean hasRecoverableFeatureEnabled() {
        return settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME)
                || settings.isFeatureEnabled(DataRegistryFeature.SESSIONS)
                || settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS)
                || settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS);
    }

    private static Instant recoveredSegmentEndTime(PlayerPlaytimeSegmentEntity segment) {
        return fallbackRecoveryTime(maxInstant(segment.getLastAccruedAt(), segment.getStartedAt()));
    }

    private static Instant fallbackRecoveryTime(Instant recoveredAt) {
        return recoveredAt == null ? UNKNOWN_RECOVERY_TIME : recoveredAt;
    }

    private static Map<Long, Integer> countOpenSessionsByPlayerId(List<PlayerSessionEntity> openSessions) {
        Map<Long, Integer> counts = new HashMap<>();
        for (PlayerSessionEntity openSession : openSessions) {
            Long playerId = playerId(openSession.getPlayer());
            if (playerId != null) {
                counts.merge(playerId, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static Long resolvePlayerId(PlayerEntity player, Long fallbackPlayerId) {
        Long playerId = playerId(player);
        return playerId == null ? fallbackPlayerId : playerId;
    }

    private static Long playerId(PlayerEntity player) {
        return player == null ? null : player.getId();
    }

    private static Long sessionId(PlayerSessionEntity session) {
        return session == null ? null : session.getId();
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

    private static boolean isAfter(Instant value, Instant reference) {
        return value != null && (reference == null || value.isAfter(reference));
    }

    private static final class RecoveryState {
        private final Map<Long, Instant> playerActivityById = new HashMap<>();
        private final Map<Long, Instant> sessionActivityById = new HashMap<>();
        private final Map<Long, PlayerEntity> playersById = new HashMap<>();

        private void rememberPlayer(PlayerEntity player) {
            Long playerId = playerId(player);
            if (playerId != null) {
                playersById.putIfAbsent(playerId, player);
            }
        }

        private PlayerEntity player(Long playerId) {
            return playersById.get(playerId);
        }

        private Set<Long> recoveredPlayerIds() {
            return new HashSet<>(playerActivityById.keySet());
        }

        private void rememberPlayerActivity(PlayerEntity player, Instant activityAt) {
            rememberPlayer(player);
            rememberPlayerActivity(playerId(player), activityAt);
        }

        private void rememberPlayerActivity(Long playerId, Instant activityAt) {
            if (playerId == null || activityAt == null) {
                return;
            }
            playerActivityById.merge(playerId, activityAt, PlayerPresenceRecoveryService::maxInstant);
        }

        private Instant playerActivity(Long playerId) {
            return playerActivityById.get(playerId);
        }

        private void rememberSessionActivity(PlayerSessionEntity session, Instant activityAt) {
            Long sessionId = sessionId(session);
            if (sessionId == null || activityAt == null) {
                return;
            }
            sessionActivityById.merge(sessionId, activityAt, PlayerPresenceRecoveryService::maxInstant);
        }

        private Instant sessionActivity(PlayerSessionEntity session) {
            return sessionActivityById.get(sessionId(session));
        }
    }
}
