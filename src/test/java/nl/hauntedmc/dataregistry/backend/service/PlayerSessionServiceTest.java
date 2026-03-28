package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerSessionServiceTest {

    @Test
    void constructorRejectsInvalidArgumentRanges() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerSessionService(registry, logger, true, true, 6, 255, 64)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerSessionService(registry, logger, true, true, 45, 0, 64)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerSessionService(registry, logger, true, true, 45, 255, 65)
        );
    }

    @Test
    void openSessionOnLoginClosesDanglingSessionAndPersistsNewSession() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        MutationQuery mutationQuery = mock(MutationQuery.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerSessionService service = new PlayerSessionService(
                registry, logger, true, false, 12, 255, 64
        );
        PlayerEntity player = persistedPlayer();
        PlayerEntity managed = persistedPlayer();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(managed);
        when(session.createMutationQuery(
                "UPDATE PlayerSessionEntity s SET s.endedAt = :end WHERE s.player.id = :playerId AND s.endedAt IS NULL"
        )).thenReturn(mutationQuery);
        when(mutationQuery.setParameter("playerId", managed.getId())).thenReturn(mutationQuery);
        when(mutationQuery.setParameter(any(String.class), any())).thenReturn(mutationQuery);
        when(mutationQuery.executeUpdate()).thenReturn(1);

        service.openSessionOnLogin(player, "  192.168.0.123  ", "example.net");

        ArgumentCaptor<PlayerSessionEntity> captor = ArgumentCaptor.forClass(PlayerSessionEntity.class);
        verify(session).persist(captor.capture());
        PlayerSessionEntity created = captor.getValue();
        assertEquals(managed, created.getPlayer());
        assertEquals("192.168.0.12", created.getIpAddress());
        assertEquals(null, created.getVirtualHost());
        assertNotNull(created.getStartedAt());
        verify(logger).info(contains("Opened session for"));
    }

    @Test
    void updateServerOnSwitchUpdatesFirstAndLastServerForOpenSession() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> query = mock(Query.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerSessionService service = new PlayerSessionService(
                registry, logger, true, true, 45, 255, 8
        );
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity openSession = new PlayerSessionEntity();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :playerId AND s.endedAt IS NULL ORDER BY s.startedAt DESC",
                PlayerSessionEntity.class
        )).thenReturn(query);
        when(query.setParameter("playerId", player.getId())).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.of(openSession));

        service.updateServerOnSwitch(player, "  minigames-01  ");

        assertEquals("minigame", openSession.getFirstServer());
        assertEquals("minigame", openSession.getLastServer());
    }

    @Test
    void closeSessionOnDisconnectSetsEndedAtWhenOpenSessionExists() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> query = mock(Query.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerSessionService service = new PlayerSessionService(
                registry, logger, true, true, 45, 255, 64
        );
        PlayerEntity player = persistedPlayer();
        PlayerSessionEntity openSession = new PlayerSessionEntity();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :playerId AND s.endedAt IS NULL ORDER BY s.startedAt DESC",
                PlayerSessionEntity.class
        )).thenReturn(query);
        when(query.setParameter("playerId", player.getId())).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.of(openSession));

        service.closeSessionOnDisconnect(player);

        assertNotNull(openSession.getEndedAt());
        verify(logger).info(contains("Closed session for"));
    }

    @Test
    void invalidEntityOrRuntimeFailureAreLoggedAndIgnored() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerSessionService service = new PlayerSessionService(
                registry, logger, true, true, 45, 255, 64
        );

        service.openSessionOnLogin(new PlayerEntity(), "1.1.1.1", "host");
        service.updateServerOnSwitch(new PlayerEntity(), "lobby");
        service.closeSessionOnDisconnect(new PlayerEntity());

        verify(logger).warn("openSessionOnLogin called with an invalid player entity.");
        verify(logger).warn("updateServerOnSwitch called with an invalid player entity.");
        verify(logger).warn("closeSessionOnDisconnect called with an invalid player entity.");

        when(registry.getORM()).thenReturn(ormContext);
        doThrow(new RuntimeException("boom")).when(ormContext).runInTransaction(any());
        PlayerEntity player = persistedPlayer();
        player.setUuid("uuid\nvalue");

        service.openSessionOnLogin(player, "1.1.1.1", "host");
        service.updateServerOnSwitch(player, "lobby");
        service.closeSessionOnDisconnect(player);

        verify(logger, times(3)).error(contains("uuid_value"), any(RuntimeException.class));
    }

    @Test
    void updateServerOnSwitchReturnsEarlyForBlankServerName() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerSessionService service = new PlayerSessionService(
                registry, logger, true, true, 45, 255, 64
        );

        when(registry.getORM()).thenReturn(ormContext);
        service.updateServerOnSwitch(persistedPlayer(), "   ");

        verify(ormContext, never()).runInTransaction(any());
    }

    @Test
    void disabledFeatureSkipsSessionTransactions() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerSessionService service = new PlayerSessionService(
                registry,
                logger,
                true,
                true,
                45,
                255,
                64,
                false
        );
        PlayerEntity player = persistedPlayer();

        service.openSessionOnLogin(player, "127.0.0.1", "mc.example.org");
        service.updateServerOnSwitch(player, "lobby-1");
        service.closeSessionOnDisconnect(player);

        verify(registry, never()).getORM();
        verify(logger, never()).warn(any());
    }

    private static PlayerEntity persistedPlayer() {
        PlayerEntity player = new PlayerEntity();
        player.setId(2L);
        player.setUuid("5636f31b-ca53-424d-a5d1-aa98a4b02e71");
        player.setUsername("Alice");
        return player;
    }
}
