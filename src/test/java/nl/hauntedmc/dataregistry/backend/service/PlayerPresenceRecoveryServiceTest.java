package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
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
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerPresenceRecoveryServiceTest {

    @Test
    void recoverAfterUncleanShutdownClosesPresenceAtLastDurableActivity() {
        TestContext context = createContext(DataRegistrySettings.builder()
                .persistIpAddress(true)
                .persistVirtualHost(true)
                .build());
        Instant sessionStartedAt = Instant.parse("2026-07-19T10:00:00Z");
        Instant visitEnteredAt = Instant.parse("2026-07-19T10:03:00Z");
        Instant lastSeenAt = Instant.parse("2026-07-19T10:04:00Z");
        Instant lastAccruedAt = Instant.parse("2026-07-19T10:05:00Z");
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity openSession = openSession(player, 101L, sessionStartedAt);
        PlayerSessionVisitEntity openVisit = openVisit(player, openSession, visitEnteredAt);
        PlayerPlaytimeSegmentEntity openSegment = openSegment(player, openSession, lastAccruedAt);
        PlayerOnlineStatusEntity onlineStatus = onlineStatus(player);
        PlayerActivitySummaryEntity summary = activitySummary(player, lastSeenAt);
        PlayerConnectionInfoEntity connectionInfo = connectionInfo(player, sessionStartedAt);

        when(context.segmentQuery.list()).thenReturn(List.of(openSegment));
        when(context.sessionQuery.list()).thenReturn(List.of(openSession));
        when(context.visitQuery.list()).thenReturn(List.of(openVisit));
        when(context.statusQuery.list()).thenReturn(List.of(onlineStatus));
        when(context.session.find(PlayerActivitySummaryEntity.class, player.getId())).thenReturn(summary);
        when(context.session.find(PlayerConnectionInfoEntity.class, player.getId())).thenReturn(connectionInfo);

        PlayerPresenceRecoveryResult result = context.service.recoverAfterUncleanShutdown();

        assertEquals(new PlayerPresenceRecoveryResult(1, 1, 1, 1, 1, 1), result);
        assertTrue(result.recoveredAnyState());
        assertEquals(PlayerPlaytimeSegmentCloseReason.RECOVERY, openSegment.getCloseReason());
        assertEquals(lastAccruedAt, openSegment.getEndedAt());
        assertEquals(lastAccruedAt, openSession.getEndedAt());
        assertEquals(lastAccruedAt, openVisit.getLeftAt());
        assertFalse(onlineStatus.isOnline());
        assertEquals("survival", onlineStatus.getPreviousServer());
        assertEquals("", onlineStatus.getCurrentServer());
        assertEquals(lastAccruedAt, summary.getLastSeenAt());
        assertEquals(lastAccruedAt, summary.getLastLogoutAt());
        assertEquals(lastAccruedAt, connectionInfo.getLastDisconnectAt());
    }

    @Test
    void recoverAfterUncleanShutdownUsesSessionStartWhenNoLaterActivityExists() {
        TestContext context = createContext(DataRegistrySettings.defaults());
        Instant sessionStartedAt = Instant.parse("2026-07-19T10:00:00Z");
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity openSession = openSession(player, 101L, sessionStartedAt);

        when(context.segmentQuery.list()).thenReturn(List.of());
        when(context.sessionQuery.list()).thenReturn(List.of(openSession));
        when(context.visitQuery.list()).thenReturn(List.of());
        when(context.statusQuery.list()).thenReturn(List.of());

        PlayerPresenceRecoveryResult result = context.service.recoverAfterUncleanShutdown();

        assertEquals(1, result.sessionsClosed());
        assertEquals(sessionStartedAt, openSession.getEndedAt());
    }

    @Test
    void recoverAfterUncleanShutdownKeepsMultipleOpenSessionsSessionScoped() {
        TestContext context = createContext(DataRegistrySettings.defaults());
        Instant firstStartedAt = Instant.parse("2026-07-19T10:00:00Z");
        Instant secondStartedAt = Instant.parse("2026-07-19T10:30:00Z");
        Instant secondLastAccruedAt = Instant.parse("2026-07-19T10:45:00Z");
        Instant playerLevelLastSeenAt = Instant.parse("2026-07-19T11:00:00Z");
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity firstSession = openSession(player, 101L, firstStartedAt);
        PlayerSessionEntity secondSession = openSession(player, 102L, secondStartedAt);
        PlayerPlaytimeSegmentEntity secondSegment = openSegment(player, secondSession, secondLastAccruedAt);
        PlayerActivitySummaryEntity summary = activitySummary(player, playerLevelLastSeenAt);

        when(context.segmentQuery.list()).thenReturn(List.of(secondSegment));
        when(context.sessionQuery.list()).thenReturn(List.of(firstSession, secondSession));
        when(context.session.find(PlayerActivitySummaryEntity.class, player.getId())).thenReturn(summary);

        PlayerPresenceRecoveryResult result = context.service.recoverAfterUncleanShutdown();

        assertEquals(1, result.playtimeSegmentsClosed());
        assertEquals(2, result.sessionsClosed());
        assertEquals(firstStartedAt, firstSession.getEndedAt());
        assertEquals(secondLastAccruedAt, secondSession.getEndedAt());
    }

    @Test
    void disabledRecoverableFeaturesSkipDatabaseWork() {
        DataRegistry registry = mock(DataRegistry.class);
        PlayerPresenceRecoveryService service = new PlayerPresenceRecoveryService(
                registry,
                mock(ILoggerAdapter.class),
                DataRegistrySettings.builder().enabledFeatures(Set.of()).build()
        );

        PlayerPresenceRecoveryResult result = service.recoverAfterUncleanShutdown();

        assertEquals(PlayerPresenceRecoveryResult.empty(), result);
        verify(registry, never()).getORM();
    }

    @Test
    void databaseFailureIsLoggedAndReturnsEmptyResult() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerPresenceRecoveryService service = new PlayerPresenceRecoveryService(
                registry,
                logger,
                DataRegistrySettings.defaults()
        );
        RuntimeException failure = new RuntimeException("database unavailable");
        when(registry.getORM()).thenReturn(ormContext);
        doThrow(failure).when(ormContext).runInTransaction(any());

        PlayerPresenceRecoveryResult result = service.recoverAfterUncleanShutdown();

        assertEquals(PlayerPresenceRecoveryResult.empty(), result);
        verify(logger).error(
                "Failed to recover stale player presence state during startup.",
                failure
        );
    }

    @Test
    void recoveryResultRejectsNegativeCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerPresenceRecoveryResult(-1, 0, 0, 0, 0, 0)
        );
    }

    private static TestContext createContext(DataRegistrySettings settings) {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> segmentQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> sessionQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionVisitEntity> visitQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerOnlineStatusEntity> statusQuery = mock(Query.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(anyString(), eq(PlayerPlaytimeSegmentEntity.class))).thenReturn(segmentQuery);
        when(session.createQuery(anyString(), eq(PlayerSessionEntity.class))).thenReturn(sessionQuery);
        when(session.createQuery(anyString(), eq(PlayerSessionVisitEntity.class))).thenReturn(visitQuery);
        when(session.createQuery(anyString(), eq(PlayerOnlineStatusEntity.class))).thenReturn(statusQuery);
        when(segmentQuery.list()).thenReturn(List.of());
        when(sessionQuery.list()).thenReturn(List.of());
        when(visitQuery.list()).thenReturn(List.of());
        when(statusQuery.list()).thenReturn(List.of());

        PlayerPresenceRecoveryService service = new PlayerPresenceRecoveryService(registry, logger, settings);
        return new TestContext(service, session, segmentQuery, sessionQuery, visitQuery, statusQuery);
    }

    private static PlayerEntity persistedPlayer() {
        PlayerEntity player = new PlayerEntity();
        player.setId(11L);
        player.setUuid("5636f31b-ca53-424d-a5d1-aa98a4b02e71");
        player.setUsername("Alice");
        return player;
    }

    private static PlayerSessionEntity openSession(PlayerEntity player, Long sessionId, Instant startedAt) {
        PlayerSessionEntity session = new PlayerSessionEntity();
        session.setId(sessionId);
        session.setPlayer(player);
        session.setStartedAt(startedAt);
        return session;
    }

    private static PlayerSessionVisitEntity openVisit(
            PlayerEntity player,
            PlayerSessionEntity session,
            Instant enteredAt
    ) {
        PlayerSessionVisitEntity visit = new PlayerSessionVisitEntity();
        visit.setId(51L);
        visit.setPlayer(player);
        visit.setSession(session);
        visit.setServerName("survival");
        visit.setEnteredAt(enteredAt);
        return visit;
    }

    private static PlayerPlaytimeSegmentEntity openSegment(
            PlayerEntity player,
            PlayerSessionEntity session,
            Instant lastAccruedAt
    ) {
        PlayerPlaytimeSegmentEntity segment = new PlayerPlaytimeSegmentEntity();
        segment.setId(71L);
        segment.setPlayer(player);
        segment.setSession(session);
        segment.setGamemodeKey("survival");
        segment.setEntryServer("survival");
        segment.setLastServer("survival");
        segment.setStartedAt(session.getStartedAt());
        segment.setLastAccruedAt(lastAccruedAt);
        return segment;
    }

    private static PlayerOnlineStatusEntity onlineStatus(PlayerEntity player) {
        PlayerOnlineStatusEntity status = new PlayerOnlineStatusEntity();
        status.setPlayer(player);
        status.setOnline(true);
        status.setCurrentServer("survival");
        return status;
    }

    private static PlayerActivitySummaryEntity activitySummary(PlayerEntity player, Instant lastSeenAt) {
        PlayerActivitySummaryEntity summary = new PlayerActivitySummaryEntity();
        summary.setPlayer(player);
        summary.setFirstSeenAt(lastSeenAt);
        summary.setLastSeenAt(lastSeenAt);
        summary.setLastLoginAt(lastSeenAt);
        return summary;
    }

    private static PlayerConnectionInfoEntity connectionInfo(PlayerEntity player, Instant lastConnectionAt) {
        PlayerConnectionInfoEntity info = new PlayerConnectionInfoEntity();
        info.setPlayer(player);
        info.setFirstConnectionAt(lastConnectionAt);
        info.setLastConnectionAt(lastConnectionAt);
        return info;
    }

    private record TestContext(
            PlayerPresenceRecoveryService service,
            Session session,
            Query<PlayerPlaytimeSegmentEntity> segmentQuery,
            Query<PlayerSessionEntity> sessionQuery,
            Query<PlayerSessionVisitEntity> visitQuery,
            Query<PlayerOnlineStatusEntity> statusQuery
    ) {
    }
}
