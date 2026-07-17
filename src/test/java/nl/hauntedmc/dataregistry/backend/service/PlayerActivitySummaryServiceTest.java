package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerActivitySummaryServiceTest {

    @Test
    void recordLoginCreatesSummaryWhenMissing() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerActivitySummaryService service = new PlayerActivitySummaryService(registry, logger);
        PlayerEntity player = persistedPlayer();
        PlayerEntity managed = persistedPlayer();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(managed);
        when(session.find(PlayerActivitySummaryEntity.class, managed.getId())).thenReturn(null);

        service.recordLogin(player);

        ArgumentCaptor<PlayerActivitySummaryEntity> captor = ArgumentCaptor.forClass(PlayerActivitySummaryEntity.class);
        verify(session).persist(captor.capture());
        PlayerActivitySummaryEntity summary = captor.getValue();
        assertEquals(managed, summary.getPlayer());
        assertNotNull(summary.getFirstSeenAt());
        assertNotNull(summary.getLastSeenAt());
        assertNotNull(summary.getLastLoginAt());
    }

    @Test
    void recordDisconnectUpdatesExistingSummary() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerActivitySummaryService service = new PlayerActivitySummaryService(registry, logger);
        PlayerEntity player = persistedPlayer();
        PlayerEntity managed = persistedPlayer();
        PlayerActivitySummaryEntity summary = new PlayerActivitySummaryEntity();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(managed);
        when(session.find(PlayerActivitySummaryEntity.class, managed.getId())).thenReturn(summary);

        service.recordDisconnect(player);

        assertNotNull(summary.getLastSeenAt());
        assertNotNull(summary.getLastLogoutAt());
    }

    @Test
    void recordSeenTouchesLastSeenWithoutLoginOrLogout() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerActivitySummaryService service = new PlayerActivitySummaryService(registry, logger);
        PlayerEntity player = persistedPlayer();
        PlayerEntity managed = persistedPlayer();
        PlayerActivitySummaryEntity summary = new PlayerActivitySummaryEntity();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(managed);
        when(session.find(PlayerActivitySummaryEntity.class, managed.getId())).thenReturn(summary);

        service.recordSeen(player);

        assertNotNull(summary.getLastSeenAt());
        org.junit.jupiter.api.Assertions.assertNull(summary.getLastLoginAt());
        org.junit.jupiter.api.Assertions.assertNull(summary.getLastLogoutAt());
    }

    @Test
    void invalidEntityDisabledFeatureAndRuntimeFailuresAreHandled() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerActivitySummaryService service = new PlayerActivitySummaryService(registry, logger);
        PlayerEntity invalid = new PlayerEntity();

        service.recordLogin(invalid);
        service.recordSeen(invalid);
        service.recordDisconnect(invalid);

        verify(logger).warn("recordLogin called with an invalid player entity.");
        verify(logger).warn("recordSeen called with an invalid player entity.");
        verify(logger).warn("recordDisconnect called with an invalid player entity.");

        PlayerActivitySummaryService disabled = new PlayerActivitySummaryService(registry, logger, false);
        disabled.recordLogin(persistedPlayer());
        disabled.recordSeen(persistedPlayer());
        disabled.recordDisconnect(persistedPlayer());
        verify(registry, never()).getORM();

        when(registry.getORM()).thenReturn(ormContext);
        doThrow(new RuntimeException("boom")).when(ormContext).runInTransaction(any());
        PlayerEntity player = persistedPlayer();
        player.setUuid("uuid\nvalue");

        service.recordLogin(player);
        service.recordSeen(player);
        service.recordDisconnect(player);

        verify(logger, times(3)).error(contains("uuid_value"), any(RuntimeException.class));
    }

    private static PlayerEntity persistedPlayer() {
        PlayerEntity player = new PlayerEntity();
        player.setId(8L);
        player.setUuid("3abca326-b548-43d4-a0b4-3e474e0c62c5");
        player.setUsername("Alice");
        return player;
    }
}
