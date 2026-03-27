package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStatusServiceTest {

    @Test
    void constructorRejectsInvalidArguments() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        assertThrows(NullPointerException.class, () -> new PlayerStatusService(null, logger, 64));
        assertThrows(NullPointerException.class, () -> new PlayerStatusService(registry, null, 64));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStatusService(registry, logger, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStatusService(registry, logger, 65));
    }

    @Test
    void updateStatusPersistsStatusWhenMissing() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerStatusService service = new PlayerStatusService(registry, logger, 6);
        PlayerEntity player = persistedPlayer();
        PlayerEntity managed = persistedPlayer();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(managed);
        when(session.find(PlayerOnlineStatusEntity.class, managed.getId())).thenReturn(null);

        service.updateStatus(player, "  Lobby-1  ");

        ArgumentCaptor<PlayerOnlineStatusEntity> captor = ArgumentCaptor.forClass(PlayerOnlineStatusEntity.class);
        verify(session).persist(captor.capture());
        PlayerOnlineStatusEntity persisted = captor.getValue();
        assertEquals(managed, persisted.getPlayer());
        assertEquals("Lobby-", persisted.getCurrentServer());
        assertEquals(true, persisted.isOnline());
    }

    @Test
    void updateStatusUpdatesExistingStatus() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerStatusService service = new PlayerStatusService(registry, logger, 12);
        PlayerEntity player = persistedPlayer();
        PlayerEntity managed = persistedPlayer();
        PlayerOnlineStatusEntity status = new PlayerOnlineStatusEntity();
        status.setPlayer(managed);
        status.setCurrentServer("old-server");

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(managed);
        when(session.find(PlayerOnlineStatusEntity.class, managed.getId())).thenReturn(status);

        service.updateStatus(player, "new-server");

        assertEquals("old-server", status.getPreviousServer());
        assertEquals("new-server", status.getCurrentServer());
        assertEquals(true, status.isOnline());
    }

    @Test
    void updateStatusOnQuitMarksStatusOffline() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerStatusService service = new PlayerStatusService(registry, logger, 64);
        PlayerEntity player = persistedPlayer();
        PlayerEntity managed = persistedPlayer();
        PlayerOnlineStatusEntity status = new PlayerOnlineStatusEntity();
        status.setPlayer(managed);
        status.setOnline(true);
        status.setCurrentServer("minigames-1");

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(managed);
        when(session.find(PlayerOnlineStatusEntity.class, managed.getId())).thenReturn(status);

        service.updateStatusOnQuit(player);

        assertFalse(status.isOnline());
        assertEquals("minigames-1", status.getPreviousServer());
        assertEquals("", status.getCurrentServer());
    }

    @Test
    void updateStatusAndQuitWarnForInvalidPlayerOrLogRuntimeFailure() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerStatusService service = new PlayerStatusService(registry, logger, 64);

        service.updateStatus(new PlayerEntity(), "lobby");
        service.updateStatusOnQuit(new PlayerEntity());
        verify(logger).warn("updateStatus called with an invalid player entity.");
        verify(logger).warn("updateStatusOnQuit called with an invalid player entity.");

        when(registry.getORM()).thenReturn(ormContext);
        doThrow(new RuntimeException("tx failed")).when(ormContext).runInTransaction(org.mockito.ArgumentMatchers.any());
        PlayerEntity player = persistedPlayer();
        player.setUuid("uuid\nvalue");

        service.updateStatus(player, "lobby");
        service.updateStatusOnQuit(player);

        verify(logger, times(2)).error(contains("uuid_value"), org.mockito.ArgumentMatchers.any(RuntimeException.class));
    }

    @Test
    void updateStatusOnQuitDoesNothingWhenStatusIsMissing() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerStatusService service = new PlayerStatusService(registry, logger, 64);
        PlayerEntity player = persistedPlayer();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(player);
        when(session.find(PlayerOnlineStatusEntity.class, player.getId())).thenReturn(null);

        service.updateStatusOnQuit(player);

        verify(session, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    private static PlayerEntity persistedPlayer() {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setUuid("55f8e8cd-73be-44e5-8af6-a4f05248d6fb");
        player.setUsername("Alice");
        return player;
    }
}
