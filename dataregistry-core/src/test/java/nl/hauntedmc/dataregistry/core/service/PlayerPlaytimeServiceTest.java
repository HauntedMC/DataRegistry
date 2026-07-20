package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeSegmentCloseReason;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;
import nl.hauntedmc.dataregistry.core.playtime.PlaytimeGamemodeResolver;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerPlaytimeServiceTest {

    @Test
    void constructorRejectsInvalidArgumentRanges() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlaytimeGamemodeResolver resolver = new PlaytimeGamemodeResolver(PlaytimeTrackingSettings.defaults());

        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerPlaytimeService(registry, logger, resolver, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerPlaytimeService(registry, logger, resolver, 65)
        );
    }

    @Test
    void onServerSwitchOpensTrackedSegmentWhenGamemodeIsTrackable() {
        TestContext context = createContext(PlaytimeTrackingSettings.builder()
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("lobby-*", "lobby")
                ))
                .build());
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity openSession = openSession(player, 4L);

        when(context.sessionQuery.uniqueResultOptional()).thenReturn(Optional.of(openSession));
        when(context.segmentQuery.uniqueResultOptional()).thenReturn(Optional.empty());
        when(context.aggregateQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        context.service.onServerSwitch(player, "Lobby-1");

        ArgumentCaptor<Object> persistedCaptor = ArgumentCaptor.forClass(Object.class);
        verify(context.session, times(2)).persist(persistedCaptor.capture());
        assertEquals(PlayerPlaytimeEntity.class, persistedCaptor.getAllValues().get(0).getClass());
        PlayerPlaytimeEntity aggregate = (PlayerPlaytimeEntity) persistedCaptor.getAllValues().get(0);
        PlayerPlaytimeSegmentEntity segment = (PlayerPlaytimeSegmentEntity) persistedCaptor.getAllValues().get(1);
        assertEquals("lobby", aggregate.getGamemodeKey());
        assertEquals(1L, aggregate.getSegmentCount());
        assertEquals("lobby", segment.getGamemodeKey());
        assertEquals("lobby-1", segment.getEntryServer());
        assertEquals(openSession, segment.getSession());
        assertNotNull(segment.getStartedAt());
    }

    @Test
    void onServerSwitchFlushesAndStopsTrackingWhenTargetGamemodeIsIgnored() {
        TestContext context = createContext(PlaytimeTrackingSettings.builder()
                .ignoredGamemodes(Set.of("queue"))
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("queue-*", "queue")
                ))
                .build());
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity openSession = openSession(player, 4L);
        PlayerPlaytimeEntity aggregate = aggregate(player, "lobby", 5_000L);
        PlayerPlaytimeSegmentEntity openSegment = openSegment(player, openSession, "lobby", "lobby-1");
        openSegment.setLastAccruedAt(Instant.now().minusSeconds(10));

        when(context.sessionQuery.uniqueResultOptional()).thenReturn(Optional.of(openSession));
        when(context.segmentQuery.uniqueResultOptional()).thenReturn(Optional.of(openSegment));
        when(context.aggregateQuery.uniqueResultOptional()).thenReturn(Optional.of(aggregate));

        context.service.onServerSwitch(player, "queue-1");

        assertEquals(PlayerPlaytimeSegmentCloseReason.STOP_TRACKING, openSegment.getCloseReason());
        assertNotNull(openSegment.getEndedAt());
        assertTrue(aggregate.getTrackedMillis() >= 14_000L);
        verify(context.session, never()).persist(any(PlayerPlaytimeSegmentEntity.class));
    }

    @Test
    void closeActivePlaytimeOnDisconnectFlushesAndClosesCurrentSegment() {
        TestContext context = createContext(PlaytimeTrackingSettings.defaults());
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity openSession = openSession(player, 4L);
        PlayerPlaytimeEntity aggregate = aggregate(player, "lobby", 2_000L);
        PlayerPlaytimeSegmentEntity openSegment = openSegment(player, openSession, "lobby", "lobby-1");
        openSegment.setLastAccruedAt(Instant.now().minusSeconds(5));

        when(context.sessionQuery.uniqueResultOptional()).thenReturn(Optional.of(openSession));
        when(context.segmentQuery.uniqueResultOptional()).thenReturn(Optional.of(openSegment));
        when(context.aggregateQuery.uniqueResultOptional()).thenReturn(Optional.of(aggregate));

        context.service.closeActivePlaytimeOnDisconnect(player);

        assertEquals(PlayerPlaytimeSegmentCloseReason.DISCONNECT, openSegment.getCloseReason());
        assertNotNull(openSegment.getEndedAt());
        assertTrue(aggregate.getTrackedMillis() >= 6_000L);
    }

    @Test
    void flushActivePlaytimeRecoversStaleSegmentWithoutAccruingOfflineTime() {
        TestContext context = createContext(PlaytimeTrackingSettings.defaults());
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity staleSession = openSession(player, 4L);
        PlayerSessionEntity currentSession = openSession(player, 7L);
        PlayerPlaytimeEntity aggregate = aggregate(player, "lobby", 2_000L);
        PlayerPlaytimeSegmentEntity openSegment = openSegment(player, staleSession, "lobby", "lobby-1");
        Instant lastAccruedAt = Instant.now().minusSeconds(30);
        openSegment.setLastAccruedAt(lastAccruedAt);

        when(context.sessionQuery.uniqueResultOptional()).thenReturn(Optional.of(currentSession));
        when(context.segmentQuery.uniqueResultOptional()).thenReturn(Optional.of(openSegment));
        when(context.aggregateQuery.uniqueResultOptional()).thenReturn(Optional.of(aggregate));

        context.service.flushActivePlaytime(player);

        assertEquals(PlayerPlaytimeSegmentCloseReason.RECOVERY, openSegment.getCloseReason());
        assertEquals(lastAccruedAt, openSegment.getEndedAt());
        assertEquals(2_000L, aggregate.getTrackedMillis());
    }

    @Test
    void invalidEntityOrDisabledFeatureSkipWork() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerPlaytimeService disabledService = new PlayerPlaytimeService(
                registry,
                logger,
                new PlaytimeGamemodeResolver(PlaytimeTrackingSettings.defaults()),
                64,
                false
        );

        disabledService.onServerSwitch(persistedPlayer(), "lobby-1");
        disabledService.flushActivePlaytime(persistedPlayer());
        disabledService.closeActivePlaytimeOnDisconnect(persistedPlayer());
        disabledService.onServerSwitch(new PlayerEntity(), "lobby-1");

        verify(registry, never()).getORM();
    }

    private static TestContext createContext(PlaytimeTrackingSettings playtimeSettings) {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> sessionQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> segmentQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeEntity> aggregateQuery = mock(Query.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(session.createQuery(anyString(), org.mockito.ArgumentMatchers.eq(PlayerSessionEntity.class)))
                .thenReturn(sessionQuery);
        when(sessionQuery.setParameter(anyString(), any())).thenReturn(sessionQuery);
        when(sessionQuery.setMaxResults(anyInt())).thenReturn(sessionQuery);
        when(session.createQuery(anyString(), org.mockito.ArgumentMatchers.eq(PlayerPlaytimeSegmentEntity.class)))
                .thenReturn(segmentQuery);
        when(segmentQuery.setParameter(anyString(), any())).thenReturn(segmentQuery);
        when(segmentQuery.setMaxResults(anyInt())).thenReturn(segmentQuery);
        when(session.createQuery(anyString(), org.mockito.ArgumentMatchers.eq(PlayerPlaytimeEntity.class)))
                .thenReturn(aggregateQuery);
        when(aggregateQuery.setParameter(anyString(), any())).thenReturn(aggregateQuery);
        when(aggregateQuery.setMaxResults(anyInt())).thenReturn(aggregateQuery);
        PlayerPlaytimeService service = new PlayerPlaytimeService(
                registry,
                logger,
                new PlaytimeGamemodeResolver(playtimeSettings),
                64
        );
        return new TestContext(service, session, sessionQuery, segmentQuery, aggregateQuery);
    }

    private static PlayerEntity persistedPlayer() {
        PlayerEntity player = new PlayerEntity();
        player.setId(2L);
        player.setUuid("5636f31b-ca53-424d-a5d1-aa98a4b02e71");
        player.setUsername("Alice");
        return player;
    }

    private static PlayerSessionEntity openSession(PlayerEntity player, Long sessionId) {
        PlayerSessionEntity session = new PlayerSessionEntity();
        session.setId(sessionId);
        session.setPlayer(player);
        session.setStartedAt(Instant.now().minusSeconds(60));
        return session;
    }

    private static PlayerPlaytimeEntity aggregate(PlayerEntity player, String gamemodeKey, long trackedMillis) {
        PlayerPlaytimeEntity aggregate = new PlayerPlaytimeEntity();
        aggregate.setId(8L);
        aggregate.setPlayer(player);
        aggregate.setGamemodeKey(gamemodeKey);
        aggregate.setTrackedMillis(trackedMillis);
        aggregate.setSegmentCount(1L);
        aggregate.setFirstTrackedAt(Instant.now().minusSeconds(120));
        aggregate.setLastTrackedAt(Instant.now().minusSeconds(10));
        return aggregate;
    }

    private static PlayerPlaytimeSegmentEntity openSegment(
            PlayerEntity player,
            PlayerSessionEntity session,
            String gamemodeKey,
            String serverName
    ) {
        PlayerPlaytimeSegmentEntity segment = new PlayerPlaytimeSegmentEntity();
        segment.setId(12L);
        segment.setPlayer(player);
        segment.setSession(session);
        segment.setGamemodeKey(gamemodeKey);
        segment.setEntryServer(serverName);
        segment.setLastServer(serverName);
        segment.setStartedAt(Instant.now().minusSeconds(30));
        return segment;
    }

    private record TestContext(
            PlayerPlaytimeService service,
            Session session,
            Query<PlayerSessionEntity> sessionQuery,
            Query<PlayerPlaytimeSegmentEntity> segmentQuery,
            Query<PlayerPlaytimeEntity> aggregateQuery
    ) {
    }
}
