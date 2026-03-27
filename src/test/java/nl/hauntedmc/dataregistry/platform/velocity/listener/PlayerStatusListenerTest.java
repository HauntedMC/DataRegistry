package nl.hauntedmc.dataregistry.platform.velocity.listener;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.backend.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.backend.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.backend.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.Optional;
import java.util.UUID;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        PlayerStatusService statusService = new PlayerStatusService(registry, logger, 64);
        PlayerConnectionInfoService connectionService = new PlayerConnectionInfoService(registry, logger, true, true, 45, 255);
        PlayerSessionService sessionService = new PlayerSessionService(registry, logger, true, true, 45, 255, 64);
        Executor directExecutor = Runnable::run;

        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        null,
                        statusService,
                        connectionService,
                        sessionService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        null,
                        connectionService,
                        sessionService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        statusService,
                        null,
                        sessionService,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        statusService,
                        connectionService,
                        null,
                        logger,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        statusService,
                        connectionService,
                        sessionService,
                        null,
                        directExecutor
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerStatusListener(
                        playerService,
                        statusService,
                        connectionService,
                        sessionService,
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
        verify(context.ormContext, times(2)).runInTransaction(any());
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

        verify(context.ormContext, times(2)).runInTransaction(any());
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
        verify(context.ormContext, times(2)).runInTransaction(any());
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

        verify(context.ormContext, times(3)).runInTransaction(any());
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
        verify(context.ormContext, times(3)).runInTransaction(any());
        verify(context.repository).removeActivePlayer(uuid);
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

    private static TestContext createContext() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        MutationQuery mutationQuery = mock(MutationQuery.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> sessionQuery = mock(Query.class);

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);

        // Shared stubs used by all three services.
        when(session.merge(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(session.find(eq(PlayerOnlineStatusEntity.class), any())).thenReturn(null);
        when(session.find(eq(PlayerConnectionInfoEntity.class), any())).thenReturn(null);
        when(session.createMutationQuery(anyString())).thenReturn(mutationQuery);
        when(mutationQuery.setParameter(anyString(), any())).thenReturn(mutationQuery);
        when(mutationQuery.executeUpdate()).thenReturn(0);
        when(session.createQuery(anyString(), eq(PlayerSessionEntity.class))).thenReturn(sessionQuery);
        when(sessionQuery.setParameter(anyString(), any())).thenReturn(sessionQuery);
        when(sessionQuery.setMaxResults(anyInt())).thenReturn(sessionQuery);
        when(sessionQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        PlayerService playerService = new PlayerService(repository, logger);
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
                64
        );

        PlayerStatusListener listener = new PlayerStatusListener(
                playerService,
                statusService,
                connectionService,
                sessionService,
                logger,
                Runnable::run
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
