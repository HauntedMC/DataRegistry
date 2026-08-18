package nl.hauntedmc.dataregistry.core.population;

import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationOrdinalQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationResolvedGamemode;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionCause;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionType;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPopulationMembershipEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionVisitEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationScopeStateEntity;
import org.hibernate.Session;

import java.time.Instant;
import java.util.Objects;

/** Applies canonical population mutations inside the authoritative player lifecycle transaction. */
public final class PlayerPopulationService {

    private static final String SCOPE_STATE_TABLE = "population_scope_state";

    private final DataRegistry dataRegistry;
    private final boolean featureEnabled;
    private volatile Boolean persistedScopeStateTableExists;

    public PlayerPopulationService(DataRegistry dataRegistry, boolean featureEnabled) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.featureEnabled = featureEnabled;
    }

    public void onLogin(Session session, PlayerEntity player, String lifecycleEventId, Instant now) {
        if (!featureEnabled) {
            markPersistedBaselinesUnverified(session);
            return;
        }
        requirePersisted(player);
        PlayerOnlineStatusEntity previousStatus = session.find(PlayerOnlineStatusEntity.class, player.getId());
        PlayerSessionEntity openSession = findOpenSession(session, player.getId());
        ensureMembership(
                session,
                player,
                PopulationScope.network(),
                now,
                openSession == null ? null : openSession.getId(),
                null,
                lifecycleEventId,
                null,
                PopulationTransitionCause.LIVE,
                true
        );
        if (previousStatus == null || !previousStatus.isOnline()) {
            changeOnline(
                    session,
                    PopulationScope.network(),
                    1L,
                    player.getId(),
                    null,
                    PopulationTransitionCause.LIVE,
                    now
            );
        }
    }

    public void onTransfer(
            Session session,
            PlayerEntity player,
            String serverName,
            String lifecycleEventId,
            Instant now
    ) {
        if (!featureEnabled) {
            return;
        }
        requirePersisted(player);
        PlayerOnlineStatusEntity previousStatus = session.find(PlayerOnlineStatusEntity.class, player.getId());
        String previousServer = previousStatus != null && previousStatus.isOnline()
                ? previousStatus.getCurrentServer()
                : null;
        PopulationResolvedGamemode previousGamemode = dataRegistry.resolvePopulationGamemode(previousServer);
        PopulationResolvedGamemode currentGamemode = dataRegistry.resolvePopulationGamemode(serverName);
        PlayerSessionEntity openSession = findOpenSession(session, player.getId());
        PlayerSessionVisitEntity openVisit = findOpenVisit(session, player.getId());

        ensureMembership(
                session,
                player,
                PopulationScope.network(),
                now,
                openSession == null ? null : openSession.getId(),
                null,
                lifecycleEventId,
                currentGamemode.serverName(),
                PopulationTransitionCause.LIVE,
                true
        );
        if (previousStatus == null || !previousStatus.isOnline()) {
            changeOnline(
                    session,
                    PopulationScope.network(),
                    1L,
                    player.getId(),
                    currentGamemode.serverName(),
                    PopulationTransitionCause.LIVE,
                    now
            );
        }

        PopulationScope previousScope = trackedScope(previousGamemode);
        PopulationScope currentScope = trackedScope(currentGamemode);
        if (currentScope != null) {
            ensureMembership(
                    session,
                    player,
                    currentScope,
                    now,
                    openSession == null ? null : openSession.getId(),
                    openVisit == null ? null : openVisit.getId(),
                    lifecycleEventId,
                    currentGamemode.serverName(),
                    PopulationTransitionCause.LIVE,
                    true
            );
        }
        if (!Objects.equals(previousScope, currentScope)) {
            if (previousScope != null) {
                changeOnline(
                        session,
                        previousScope,
                        -1L,
                        player.getId(),
                        previousGamemode.serverName(),
                        PopulationTransitionCause.LIVE,
                        now
                );
            }
            if (currentScope != null) {
                changeOnline(
                        session,
                        currentScope,
                        1L,
                        player.getId(),
                        currentGamemode.serverName(),
                        PopulationTransitionCause.LIVE,
                        now
                );
            }
        }
    }

    public void onDisconnect(Session session, PlayerEntity player, Instant now) {
        if (!featureEnabled) {
            return;
        }
        requirePersisted(player);
        PlayerOnlineStatusEntity previousStatus = session.find(PlayerOnlineStatusEntity.class, player.getId());
        if (previousStatus == null || !previousStatus.isOnline()) {
            return;
        }
        PopulationResolvedGamemode previousGamemode = dataRegistry.resolvePopulationGamemode(
                previousStatus.getCurrentServer()
        );
        PopulationScope previousScope = trackedScope(previousGamemode);
        // Every lifecycle mutation that touches multiple population scopes acquires network first. Keeping one
        // deterministic scope-lock order prevents transfer/disconnect deadlocks under concurrent lifecycle traffic.
        changeOnline(
                session,
                PopulationScope.network(),
                -1L,
                player.getId(),
                previousGamemode.serverName(),
                PopulationTransitionCause.LIVE,
                now
        );
        if (previousScope != null) {
            changeOnline(
                    session,
                    previousScope,
                    -1L,
                    player.getId(),
                    previousGamemode.serverName(),
                    PopulationTransitionCause.LIVE,
                    now
            );
        }
    }

    private PlayerPopulationMembershipEntity ensureMembership(
            Session session,
            PlayerEntity player,
            PopulationScope scope,
            Instant firstJoinedAt,
            Long firstSessionId,
            Long firstVisitId,
            String lifecycleEventId,
            String serverName,
            PopulationTransitionCause cause,
            boolean emitTransition
    ) {
        PopulationBaselineQuality baseline = inheritedMembershipQuality(session);
        PopulationBaselineQuality peakQuality = inheritedPeakQuality(session);
        PopulationScopeStateEntity state = PopulationPersistence.ensureAndLockScopeState(
                session,
                scope,
                baseline,
                peakQuality,
                firstJoinedAt
        );
        PlayerPopulationMembershipEntity existing = PopulationPersistence.findMembership(session, player.getId(), scope);
        if (existing != null) {
            return existing;
        }
        long previousCount = state.getUniquePlayerCount();
        long ordinal = Math.addExact(previousCount, 1L);
        PlayerPopulationMembershipEntity membership = new PlayerPopulationMembershipEntity();
        membership.setPlayer(session.merge(player));
        membership.setScopeId(scope.storageKey());
        membership.setScopeType(scope.type());
        membership.setScopeKey(scope.key());
        membership.setOrdinal(ordinal);
        membership.setOrdinalQuality(PopulationOrdinalQuality.RECORDED_EXACT);
        membership.setFirstJoinedAt(firstJoinedAt);
        membership.setFirstSessionId(firstSessionId);
        membership.setFirstVisitId(firstVisitId);
        membership.setFirstLifecycleEventId(lifecycleEventId);
        membership.setCreatedAt(Instant.now());
        session.persist(membership);
        state.setUniquePlayerCount(ordinal);
        state.setUpdatedAt(firstJoinedAt);
        if (emitTransition) {
            PopulationPersistence.transition(
                    session,
                    PopulationTransitionType.MEMBERSHIP_ADDED,
                    cause,
                    scope,
                    player.getId(),
                    serverName,
                    ordinal,
                    previousCount,
                    ordinal,
                    firstJoinedAt
            );
        }
        return membership;
    }

    private void changeOnline(
            Session session,
            PopulationScope scope,
            long delta,
            Long playerId,
            String serverName,
            PopulationTransitionCause cause,
            Instant now
    ) {
        PopulationScopeStateEntity state = PopulationPersistence.ensureAndLockScopeState(
                session,
                scope,
                inheritedMembershipQuality(session),
                inheritedPeakQuality(session),
                now
        );
        long previous = state.getCurrentOnline();
        long current = delta < 0L ? Math.max(0L, previous + delta) : Math.addExact(previous, delta);
        if (current == previous) {
            return;
        }
        state.setCurrentOnline(current);
        state.setUpdatedAt(now);
        PopulationPersistence.transition(
                session,
                PopulationTransitionType.ONLINE_CHANGED,
                cause,
                scope,
                playerId,
                serverName,
                null,
                previous,
                current,
                now
        );
        if (current > state.getOnlinePeak()) {
            long previousPeak = state.getOnlinePeak();
            state.setOnlinePeak(current);
            state.setOnlinePeakAchievedAt(now);
            PopulationPersistence.transition(
                    session,
                    PopulationTransitionType.ONLINE_PEAK_CHANGED,
                    cause,
                    scope,
                    playerId,
                    serverName,
                    null,
                    previousPeak,
                    current,
                    now
            );
        }
    }

    private PopulationBaselineQuality inheritedMembershipQuality(Session session) {
        PopulationScopeStateEntity network = session.find(
                PopulationScopeStateEntity.class,
                PopulationScope.network().storageKey()
        );
        return network == null
                ? PopulationBaselineQuality.TRACKED_ONLY
                : network.getMembershipBaselineQuality();
    }

    private PopulationBaselineQuality inheritedPeakQuality(Session session) {
        PopulationScopeStateEntity network = session.find(
                PopulationScopeStateEntity.class,
                PopulationScope.network().storageKey()
        );
        return network == null ? PopulationBaselineQuality.TRACKED_ONLY : network.getPeakBaselineQuality();
    }

    private void markPersistedBaselinesUnverified(Session session) {
        if (!persistedScopeStateTableExists(session)) {
            return;
        }
        session.doWork(connection -> {
            try (var statement = connection.prepareStatement(
                    "UPDATE " + SCOPE_STATE_TABLE + " SET membership_baseline_quality = ?, peak_baseline_quality = ? " +
                            "WHERE membership_baseline_quality <> ? OR peak_baseline_quality <> ?"
            )) {
                String trackedOnly = PopulationBaselineQuality.TRACKED_ONLY.name();
                statement.setString(1, trackedOnly);
                statement.setString(2, trackedOnly);
                statement.setString(3, trackedOnly);
                statement.setString(4, trackedOnly);
                statement.executeUpdate();
            }
        });
    }

    private boolean persistedScopeStateTableExists(Session session) {
        Boolean cached = persistedScopeStateTableExists;
        if (cached != null) {
            return cached;
        }
        boolean exists = session.doReturningWork(connection -> {
            try (var tables = connection.getMetaData().getTables(
                    connection.getCatalog(),
                    null,
                    SCOPE_STATE_TABLE,
                    new String[]{"TABLE"}
            )) {
                return tables.next();
            }
        });
        persistedScopeStateTableExists = exists;
        return exists;
    }

    private static PopulationScope trackedScope(PopulationResolvedGamemode gamemode) {
        return gamemode != null && gamemode.tracked() && gamemode.gamemodeKey() != null
                ? PopulationScope.gamemode(gamemode.gamemodeKey())
                : null;
    }

    private static PlayerSessionEntity findOpenSession(Session session, long playerId) {
        return session.createQuery(
                        "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                "ORDER BY s.startedAt DESC, s.id DESC",
                        PlayerSessionEntity.class
                )
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .uniqueResult();
    }

    private static PlayerSessionVisitEntity findOpenVisit(Session session, long playerId) {
        return session.createQuery(
                        "SELECT v FROM PlayerSessionVisitEntity v WHERE v.player.id = :playerId AND v.leftAt IS NULL " +
                                "ORDER BY v.enteredAt DESC, v.id DESC",
                        PlayerSessionVisitEntity.class
                )
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .uniqueResult();
    }

    private static void requirePersisted(PlayerEntity player) {
        if (player == null || player.getId() == null) {
            throw new IllegalArgumentException("player must be persisted before population lifecycle updates.");
        }
    }
}
