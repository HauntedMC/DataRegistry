package nl.hauntedmc.dataregistry.platform.velocity.listener;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeSegmentCloseReason;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionVisitEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.backend.config.PlaytimeTrackingSettings;
import nl.hauntedmc.dataregistry.backend.playtime.PlaytimeGamemodeResolver;
import nl.hauntedmc.dataregistry.backend.service.PlayerActivitySummaryService;
import nl.hauntedmc.dataregistry.backend.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.backend.service.PlayerNameHistoryService;
import nl.hauntedmc.dataregistry.backend.service.PlayerPlaytimeService;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.backend.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.backend.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStatusListenerTest {

    @Test
    void constructorRejectsNullDependencies() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        when(registry.getORM()).thenReturn(ormContext);
        PlayerService playerService = new PlayerService(repository, logger);
        PlayerNameHistoryService nameHistoryService = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerActivitySummaryService activitySummaryService = new PlayerActivitySummaryService(registry, logger, true);
        PlayerStatusService statusService = new PlayerStatusService(registry, logger, 64);
        PlayerConnectionInfoService connectionService = new PlayerConnectionInfoService(registry, logger, true, true, 45, 255);
        PlayerSessionService sessionService = new PlayerSessionService(registry, logger, true, true, 45, 255, 64);
        PlayerPlaytimeService playtimeService = new PlayerPlaytimeService(
                registry,
                logger,
                new PlaytimeGamemodeResolver(PlaytimeTrackingSettings.defaults()),
                64
        );
        Executor directExecutor = Runnable::run;

        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        null,
                        nameHistoryService,
                        activitySummaryService,
                        statusService,
                        connectionService,
                        sessionService,
                        playtimeService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        null,
                        activitySummaryService,
                        statusService,
                        connectionService,
                        sessionService,
                        playtimeService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        nameHistoryService,
                        null,
                        statusService,
                        connectionService,
                        sessionService,
                        playtimeService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        nameHistoryService,
                        activitySummaryService,
                        null,
                        connectionService,
                        sessionService,
                        playtimeService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        nameHistoryService,
                        activitySummaryService,
                        statusService,
                        null,
                        sessionService,
                        playtimeService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        nameHistoryService,
                        activitySummaryService,
                        statusService,
                        connectionService,
                        null,
                        playtimeService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        nameHistoryService,
                        activitySummaryService,
                        statusService,
                        connectionService,
                        sessionService,
                        null,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        nameHistoryService,
                        activitySummaryService,
                        statusService,
                        connectionService,
                        sessionService,
                        playtimeService,
                        null,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        nameHistoryService,
                        activitySummaryService,
                        statusService,
                        connectionService,
                        sessionService,
                        playtimeService,
                        logger,
                        null
                )
        );
    }

    @Test
    void onPlayerJoinPersistsPlayerAndRunsConnectionAndSessionTransactions() throws Exception {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        when(context.repository.getActivePlayer(uuid)).thenReturn(Optional.empty(), Optional.of(persistent));
        when(context.repository.getOrCreateActivePlayer(uuid, "Alice")).thenReturn(persistent);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");
        when(player.getRemoteAddress()).thenReturn(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 25565)
        );
        when(player.getVirtualHost()).thenReturn(Optional.of(new InetSocketAddress("mc.example.org", 25566)));

        context.listener.onPlayerJoin(new PostLoginEvent(player));

        verify(context.repository).getOrCreateActivePlayer(uuid, "Alice");
        verify(context.ormContext, times(3)).runInTransaction(any());
    }

    @Test
    void onPlayerJoinRecordsRenameHistoryWhenKnownUsernameChanged() throws Exception {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        PlayerEntity previous = persistedPlayer(uuid, "OldName");
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        when(context.repository.getActivePlayer(uuid)).thenReturn(Optional.empty(), Optional.of(persistent));
        when(context.repository.findByUUID(uuid)).thenReturn(Optional.of(previous));
        when(context.repository.getOrCreateActivePlayer(uuid, "Alice")).thenReturn(persistent);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");
        when(player.getRemoteAddress()).thenReturn(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 25565)
        );
        when(player.getVirtualHost()).thenReturn(Optional.of(new InetSocketAddress("mc.example.org", 25566)));

        context.listener.onPlayerJoin(new PostLoginEvent(player));

        verify(context.ormContext, times(4)).runInTransaction(any());
    }

    @Test
    void onServerSwitchUpdatesStatusAndSessionForActivePlayer() {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        when(context.repository.getActivePlayer(uuid)).thenReturn(Optional.of(persistent));
        reset(context.ormContext);
        executeTransactionsWithSession(context.ormContext, context.session);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(new ServerInfo("lobby-1", new InetSocketAddress("127.0.0.1", 25567)));

        context.listener.onServerSwitch(new ServerConnectedEvent(player, server, null));

        verify(context.ormContext, times(4)).runInTransaction(any());
    }

    @Test
    void onServerSwitchRestoresPlayerWhenActiveCacheMisses() {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        when(context.repository.getActivePlayer(uuid)).thenReturn(Optional.empty());
        when(context.repository.getOrCreateActivePlayer(uuid, "Alice")).thenReturn(persistent);
        reset(context.ormContext);
        executeTransactionsWithSession(context.ormContext, context.session);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(new ServerInfo("lobby-1", new InetSocketAddress("127.0.0.1", 25567)));

        context.listener.onServerSwitch(new ServerConnectedEvent(player, server, null));

        verify(context.repository).getOrCreateActivePlayer(uuid, "Alice");
        verify(context.ormContext, times(4)).runInTransaction(any());
    }

    @Test
    void onServerSwitchInvokesPlaytimeAfterStatusAndSessionUpdate() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> sessionUpdateQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> playtimeSessionQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> playtimeSegmentQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeEntity> playtimeAggregateQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionVisitEntity> sessionVisitQuery = mock(Query.class);
        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(session.find(eq(PlayerOnlineStatusEntity.class), any())).thenReturn(null);
        when(session.find(eq(PlayerActivitySummaryEntity.class), any())).thenReturn(null);
        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s " +
                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                        "ORDER BY s.startedAt DESC, s.id DESC",
                PlayerSessionEntity.class
        )).thenReturn(sessionUpdateQuery, playtimeSessionQuery);
        when(sessionUpdateQuery.setParameter(anyString(), any())).thenReturn(sessionUpdateQuery);
        when(sessionUpdateQuery.setMaxResults(1)).thenReturn(sessionUpdateQuery);
        when(playtimeSessionQuery.setParameter(anyString(), any())).thenReturn(playtimeSessionQuery);
        when(playtimeSessionQuery.setMaxResults(1)).thenReturn(playtimeSessionQuery);
        PlayerSessionEntity openSession = new PlayerSessionEntity();
        openSession.setPlayer(persistedPlayer("00000000-0000-0000-0000-000000000001", "Alice"));
        openSession.setId(4L);
        when(sessionUpdateQuery.uniqueResultOptional()).thenReturn(Optional.of(openSession));
        when(playtimeSessionQuery.uniqueResultOptional()).thenReturn(Optional.of(openSession));
        when(session.createQuery(
                "SELECT v FROM PlayerSessionVisitEntity v " +
                        "WHERE v.player.id = :playerId AND v.leftAt IS NULL " +
                        "ORDER BY v.enteredAt DESC, v.id DESC",
                PlayerSessionVisitEntity.class
        )).thenReturn(sessionVisitQuery);
        when(sessionVisitQuery.setParameter(anyString(), any())).thenReturn(sessionVisitQuery);
        when(sessionVisitQuery.setMaxResults(1)).thenReturn(sessionVisitQuery);
        when(sessionVisitQuery.uniqueResultOptional()).thenReturn(Optional.empty());
        when(session.createQuery(
                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                        "ORDER BY s.startedAt DESC, s.id DESC",
                PlayerPlaytimeSegmentEntity.class
        )).thenReturn(playtimeSegmentQuery);
        when(playtimeSegmentQuery.setParameter(anyString(), any())).thenReturn(playtimeSegmentQuery);
        when(playtimeSegmentQuery.setMaxResults(1)).thenReturn(playtimeSegmentQuery);
        when(playtimeSegmentQuery.uniqueResultOptional()).thenReturn(Optional.empty());
        when(session.createQuery(
                "SELECT p FROM PlayerPlaytimeEntity p " +
                        "WHERE p.player.id = :playerId AND p.gamemodeKey = :gamemodeKey",
                PlayerPlaytimeEntity.class
        )).thenReturn(playtimeAggregateQuery);
        when(playtimeAggregateQuery.setParameter(anyString(), any())).thenReturn(playtimeAggregateQuery);
        when(playtimeAggregateQuery.setMaxResults(1)).thenReturn(playtimeAggregateQuery);
        when(playtimeAggregateQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        PlayerService playerService = new PlayerService(repository, logger);
        PlayerNameHistoryService nameHistoryService = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerActivitySummaryService activitySummaryService = new PlayerActivitySummaryService(registry, logger, true);
        PlayerStatusService statusService = new PlayerStatusService(registry, logger, 64);
        PlayerConnectionInfoService connectionService = new PlayerConnectionInfoService(registry, logger, true, true, 45, 255);
        PlayerSessionService sessionService = new PlayerSessionService(registry, logger, true, true, 45, 255, 64, true, true);
        PlayerPlaytimeService playtimeService = new PlayerPlaytimeService(
                registry,
                logger,
                new PlaytimeGamemodeResolver(PlaytimeTrackingSettings.defaults()),
                64
        );
        PlayerStatusListener listener = new PlayerStatusListener(
                playerService,
                nameHistoryService,
                activitySummaryService,
                statusService,
                connectionService,
                sessionService,
                playtimeService,
                logger,
                Runnable::run
        );

        String uuid = UUID.randomUUID().toString();
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        when(repository.getActivePlayer(uuid)).thenReturn(Optional.of(persistent));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(new ServerInfo("lobby-1", new InetSocketAddress("127.0.0.1", 25567)));

        listener.onServerSwitch(new ServerConnectedEvent(player, server, null));

        InOrder inOrder = inOrder(session, sessionUpdateQuery, sessionVisitQuery, playtimeSessionQuery, playtimeSegmentQuery);
        inOrder.verify(session).find(PlayerOnlineStatusEntity.class, persistent.getId());
        inOrder.verify(sessionUpdateQuery).uniqueResultOptional();
        inOrder.verify(sessionVisitQuery).uniqueResultOptional();
        inOrder.verify(playtimeSessionQuery).uniqueResultOptional();
        inOrder.verify(playtimeSegmentQuery).uniqueResultOptional();
    }

    @Test
    void onPlayerQuitRunsStatusConnectionAndSessionForActivePlayerAndRemovesCache() {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        when(context.repository.getActivePlayer(uuid)).thenReturn(Optional.of(persistent));
        reset(context.ormContext);
        executeTransactionsWithSession(context.ormContext, context.session);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");

        context.listener.onPlayerQuit(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));

        verify(context.ormContext, times(5)).runInTransaction(any());
        verify(context.repository).removeActivePlayer(uuid);
    }

    @Test
    void onPlayerQuitRestoresPlayerAndRunsLifecycleWhenCacheMisses() {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        when(context.repository.getActivePlayer(uuid)).thenReturn(Optional.empty());
        when(context.repository.getOrCreateActivePlayer(uuid, "Alice")).thenReturn(persistent);
        reset(context.ormContext);
        executeTransactionsWithSession(context.ormContext, context.session);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");

        context.listener.onPlayerQuit(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));

        verify(context.repository).getOrCreateActivePlayer(uuid, "Alice");
        verify(context.ormContext, times(5)).runInTransaction(any());
        verify(context.repository).removeActivePlayer(uuid);
    }

    @Test
    void onPlayerQuitClosesPlaytimeBeforeClosingSessionAndRemovesCacheLast() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> playtimeSegmentQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> playtimeSessionQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> closeSessionQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionVisitEntity> closeVisitQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeEntity> playtimeAggregateQuery = mock(Query.class);
        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlayerOnlineStatusEntity onlineStatus = new PlayerOnlineStatusEntity();
        onlineStatus.setCurrentServer("lobby-1");
        when(session.find(eq(PlayerOnlineStatusEntity.class), any())).thenReturn(onlineStatus);
        when(session.find(eq(PlayerActivitySummaryEntity.class), any())).thenReturn(null);
        when(session.find(eq(PlayerConnectionInfoEntity.class), any())).thenReturn(new PlayerConnectionInfoEntity());
        when(session.createQuery(
                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                        "ORDER BY s.startedAt DESC, s.id DESC",
                PlayerPlaytimeSegmentEntity.class
        )).thenReturn(playtimeSegmentQuery);
        when(playtimeSegmentQuery.setParameter(anyString(), any())).thenReturn(playtimeSegmentQuery);
        when(playtimeSegmentQuery.setMaxResults(1)).thenReturn(playtimeSegmentQuery);
        PlayerEntity persistent = persistedPlayer(UUID.randomUUID().toString(), "Alice");
        PlayerSessionEntity openSession = new PlayerSessionEntity();
        openSession.setId(15L);
        openSession.setPlayer(persistent);
        PlayerPlaytimeSegmentEntity openSegment = new PlayerPlaytimeSegmentEntity();
        openSegment.setId(16L);
        openSegment.setPlayer(persistent);
        openSegment.setSession(openSession);
        openSegment.setGamemodeKey("lobby");
        openSegment.setEntryServer("lobby-1");
        openSegment.setLastServer("lobby-1");
        openSegment.setStartedAt(java.time.Instant.now().minusSeconds(30));
        openSegment.setLastAccruedAt(java.time.Instant.now().minusSeconds(5));
        openSegment.setCloseReason(PlayerPlaytimeSegmentCloseReason.DISCONNECT);
        when(playtimeSegmentQuery.uniqueResultOptional()).thenReturn(Optional.of(openSegment));
        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s " +
                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                        "ORDER BY s.startedAt DESC, s.id DESC",
                PlayerSessionEntity.class
        )).thenReturn(playtimeSessionQuery, closeSessionQuery);
        when(playtimeSessionQuery.setParameter(anyString(), any())).thenReturn(playtimeSessionQuery);
        when(playtimeSessionQuery.setMaxResults(1)).thenReturn(playtimeSessionQuery);
        when(playtimeSessionQuery.uniqueResultOptional()).thenReturn(Optional.of(openSession));
        when(session.createQuery(
                "SELECT v FROM PlayerSessionVisitEntity v " +
                        "WHERE v.player.id = :playerId AND v.leftAt IS NULL " +
                        "ORDER BY v.enteredAt DESC, v.id DESC",
                PlayerSessionVisitEntity.class
        )).thenReturn(closeVisitQuery);
        when(closeVisitQuery.setParameter(anyString(), any())).thenReturn(closeVisitQuery);
        when(closeVisitQuery.setMaxResults(1)).thenReturn(closeVisitQuery);
        when(closeVisitQuery.uniqueResultOptional()).thenReturn(Optional.empty());
        when(closeSessionQuery.setParameter(anyString(), any())).thenReturn(closeSessionQuery);
        when(closeSessionQuery.setMaxResults(1)).thenReturn(closeSessionQuery);
        when(closeSessionQuery.uniqueResultOptional()).thenReturn(Optional.of(openSession));
        when(session.createQuery(
                "SELECT p FROM PlayerPlaytimeEntity p " +
                        "WHERE p.player.id = :playerId AND p.gamemodeKey = :gamemodeKey",
                PlayerPlaytimeEntity.class
        )).thenReturn(playtimeAggregateQuery);
        when(playtimeAggregateQuery.setParameter(anyString(), any())).thenReturn(playtimeAggregateQuery);
        when(playtimeAggregateQuery.setMaxResults(1)).thenReturn(playtimeAggregateQuery);
        PlayerPlaytimeEntity aggregate = new PlayerPlaytimeEntity();
        aggregate.setPlayer(persistent);
        aggregate.setGamemodeKey("lobby");
        aggregate.setTrackedMillis(0L);
        aggregate.setSegmentCount(1L);
        aggregate.setFirstTrackedAt(openSegment.getStartedAt());
        aggregate.setLastTrackedAt(openSegment.getLastAccruedAt());
        when(playtimeAggregateQuery.uniqueResultOptional()).thenReturn(Optional.of(aggregate));

        PlayerService playerService = new PlayerService(repository, logger);
        PlayerNameHistoryService nameHistoryService = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerActivitySummaryService activitySummaryService = new PlayerActivitySummaryService(registry, logger, true);
        PlayerStatusService statusService = new PlayerStatusService(registry, logger, 64);
        PlayerConnectionInfoService connectionService = new PlayerConnectionInfoService(registry, logger, true, true, 45, 255);
        PlayerSessionService sessionService = new PlayerSessionService(registry, logger, true, true, 45, 255, 64);
        PlayerPlaytimeService playtimeService = new PlayerPlaytimeService(
                registry,
                logger,
                new PlaytimeGamemodeResolver(PlaytimeTrackingSettings.defaults()),
                64
        );
        PlayerStatusListener listener = new PlayerStatusListener(
                playerService,
                nameHistoryService,
                activitySummaryService,
                statusService,
                connectionService,
                sessionService,
                playtimeService,
                logger,
                Runnable::run
        );

        String uuid = persistent.getUuid();
        when(repository.getActivePlayer(uuid)).thenReturn(Optional.of(persistent));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");

        listener.onPlayerQuit(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));

        InOrder inOrder = inOrder(
                session,
                playtimeSegmentQuery,
                playtimeSessionQuery,
                playtimeAggregateQuery,
                closeVisitQuery,
                closeSessionQuery,
                repository
        );
        inOrder.verify(session).find(PlayerOnlineStatusEntity.class, persistent.getId());
        inOrder.verify(session).find(PlayerActivitySummaryEntity.class, persistent.getId());
        inOrder.verify(session).find(PlayerConnectionInfoEntity.class, persistent.getId());
        inOrder.verify(playtimeSegmentQuery).uniqueResultOptional();
        inOrder.verify(playtimeSessionQuery).uniqueResultOptional();
        inOrder.verify(playtimeAggregateQuery).uniqueResultOptional();
        inOrder.verify(closeVisitQuery).uniqueResultOptional();
        inOrder.verify(closeSessionQuery).uniqueResultOptional();
        inOrder.verify(repository).removeActivePlayer(uuid);
    }

    @Test
    void onPlayerQuitSkipsLifecycleTransactionsWhenLoginFailed() {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        when(context.repository.getActivePlayer(uuid)).thenReturn(Optional.empty());
        reset(context.ormContext);
        executeTransactionsWithSession(context.ormContext, context.session);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");

        context.listener.onPlayerQuit(new DisconnectEvent(player, DisconnectEvent.LoginStatus.CANCELLED_BY_PROXY));

        verify(context.ormContext, never()).runInTransaction(any());
        verify(context.repository, never()).getOrCreateActivePlayer(anyString(), anyString());
        verify(context.repository).removeActivePlayer(uuid);
    }

    @Test
    void beginShutdownStopsAcceptingNewEvents() {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        context.listener.beginShutdown();

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");

        context.listener.onPlayerJoin(new PostLoginEvent(player));

        verify(context.repository, never()).getOrCreateActivePlayer(anyString(), anyString());
        verify(context.ormContext, never()).runInTransaction(any());
        assertTrue(context.listener.awaitPipelineDrain(1L, TimeUnit.MILLISECONDS));
    }

    @Test
    void awaitPipelineDrainTimesOutWhenExecutorDoesNotRunQueuedTasks() {
        TestContext context = createContext(runnable -> {
        });
        String uuid = UUID.randomUUID().toString();

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");

        context.listener.onPlayerJoin(new PostLoginEvent(player));
        context.listener.beginShutdown();

        assertFalse(context.listener.awaitPipelineDrain(1L, TimeUnit.MILLISECONDS));
    }

    @Test
    void onPlayerJoinPersistsIdentityBeforeQueuedFollowUpRuns() {
        TestContext context = createContext(runnable -> {
        });
        String uuid = UUID.randomUUID().toString();
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        when(context.repository.getOrCreateActivePlayer(uuid, "Alice")).thenReturn(persistent);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");

        context.listener.onPlayerJoin(new PostLoginEvent(player));

        verify(context.repository).getOrCreateActivePlayer(uuid, "Alice");
        verify(context.ormContext, never()).runInTransaction(any());
    }

    @Test
    void onPlayerJoinRestoresActiveCacheAfterQueuedQuitRemovesIt() {
        Deque<Runnable> queuedTasks = new ArrayDeque<>();
        TestContext context = createContext(queuedTasks::addLast);
        String uuid = UUID.randomUUID().toString();
        PlayerEntity persistent = persistedPlayer(uuid, "Alice");
        Map<String, PlayerEntity> activePlayers = new ConcurrentHashMap<>();
        when(context.repository.getActivePlayer(uuid)).thenAnswer(invocation ->
                Optional.ofNullable(activePlayers.get(uuid))
        );
        when(context.repository.getOrCreateActivePlayer(uuid, "Alice")).thenAnswer(invocation -> {
            activePlayers.put(uuid, persistent);
            return persistent;
        });
        doAnswer(invocation -> {
            activePlayers.remove(uuid);
            return null;
        }).when(context.repository).removeActivePlayer(uuid);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(player.getUsername()).thenReturn("Alice");

        context.listener.onPlayerQuit(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));
        context.listener.onPlayerJoin(new PostLoginEvent(player));

        assertEquals(1, queuedTasks.size());
        queuedTasks.removeFirst().run();
        assertTrue(activePlayers.isEmpty());

        assertEquals(1, queuedTasks.size());
        queuedTasks.removeFirst().run();
        assertEquals(persistent, activePlayers.get(uuid));
        verify(context.repository, times(2)).getOrCreateActivePlayer(uuid, "Alice");
    }

    @Test
    void flushActivePlaytimeQueuesOneTaskPerActivePlayer() {
        TestContext context = createContext();
        String uuid = UUID.randomUUID().toString();
        when(context.repository.snapshotActivePlayers()).thenReturn(Map.of(uuid, persistedPlayer(uuid, "Alice")));

        context.listener.flushActivePlaytime();

        verify(context.ormContext).runInTransaction(any());
    }

    private static TestContext createContext() {
        return createContext(Runnable::run);
    }

    private static TestContext createContext(Executor eventExecutor) {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        MutationQuery mutationQuery = mock(MutationQuery.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> sessionQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionVisitEntity> sessionVisitQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerNameHistoryEntity> nameHistoryQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> playtimeSegmentQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeEntity> playtimeAggregateQuery = mock(Query.class);

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);

        // Shared stubs used by all three services.
        when(session.merge(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(session.find(eq(PlayerActivitySummaryEntity.class), any())).thenReturn(null);
        when(session.find(eq(PlayerOnlineStatusEntity.class), any())).thenReturn(null);
        when(session.find(eq(PlayerConnectionInfoEntity.class), any())).thenReturn(null);
        when(session.createMutationQuery(anyString())).thenReturn(mutationQuery);
        when(mutationQuery.setParameter(anyString(), any())).thenReturn(mutationQuery);
        when(mutationQuery.executeUpdate()).thenReturn(0);
        when(session.createQuery(anyString(), eq(PlayerSessionEntity.class))).thenReturn(sessionQuery);
        when(sessionQuery.setParameter(anyString(), any())).thenReturn(sessionQuery);
        when(sessionQuery.setMaxResults(anyInt())).thenReturn(sessionQuery);
        when(sessionQuery.uniqueResultOptional()).thenReturn(Optional.empty());
        when(session.createQuery(anyString(), eq(PlayerSessionVisitEntity.class))).thenReturn(sessionVisitQuery);
        when(sessionVisitQuery.setParameter(anyString(), any())).thenReturn(sessionVisitQuery);
        when(sessionVisitQuery.setMaxResults(anyInt())).thenReturn(sessionVisitQuery);
        when(sessionVisitQuery.uniqueResultOptional()).thenReturn(Optional.empty());
        when(session.createQuery(anyString(), eq(PlayerNameHistoryEntity.class))).thenReturn(nameHistoryQuery);
        when(nameHistoryQuery.setParameter(anyString(), any())).thenReturn(nameHistoryQuery);
        when(nameHistoryQuery.setMaxResults(anyInt())).thenReturn(nameHistoryQuery);
        when(nameHistoryQuery.uniqueResultOptional()).thenReturn(Optional.empty());
        when(session.createQuery(anyString(), eq(PlayerPlaytimeSegmentEntity.class))).thenReturn(playtimeSegmentQuery);
        when(playtimeSegmentQuery.setParameter(anyString(), any())).thenReturn(playtimeSegmentQuery);
        when(playtimeSegmentQuery.setMaxResults(anyInt())).thenReturn(playtimeSegmentQuery);
        when(playtimeSegmentQuery.uniqueResultOptional()).thenReturn(Optional.empty());
        when(session.createQuery(anyString(), eq(PlayerPlaytimeEntity.class))).thenReturn(playtimeAggregateQuery);
        when(playtimeAggregateQuery.setParameter(anyString(), any())).thenReturn(playtimeAggregateQuery);
        when(playtimeAggregateQuery.setMaxResults(anyInt())).thenReturn(playtimeAggregateQuery);
        when(playtimeAggregateQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        PlayerService playerService = new PlayerService(repository, logger);
        PlayerNameHistoryService nameHistoryService = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerActivitySummaryService activitySummaryService = new PlayerActivitySummaryService(registry, logger, true);
        PlayerStatusService statusService = new PlayerStatusService(registry, logger, 64);
        PlayerConnectionInfoService connectionService = new PlayerConnectionInfoService(
                registry,
                logger,
                true,
                true,
                45,
                255
        );
        PlayerSessionService sessionService = new PlayerSessionService(
                registry,
                logger,
                true,
                true,
                45,
                255,
                64,
                true,
                true
        );
        PlayerPlaytimeService playtimeService = new PlayerPlaytimeService(
                registry,
                logger,
                new PlaytimeGamemodeResolver(PlaytimeTrackingSettings.defaults()),
                64
        );

        PlayerStatusListener listener = new PlayerStatusListener(
                playerService,
                nameHistoryService,
                activitySummaryService,
                statusService,
                connectionService,
                sessionService,
                playtimeService,
                logger,
                eventExecutor
        );
        return new TestContext(listener, repository, ormContext, session);
    }

    private static PlayerEntity persistedPlayer(String uuid, String username) {
        PlayerEntity player = new PlayerEntity();
        player.setId(10L);
        player.setUuid(uuid);
        player.setUsername(username);
        return player;
    }

    private record TestContext(
            PlayerStatusListener listener,
            PlayerRepository repository,
            ORMContext ormContext,
            Session session
    ) {
    }
}
