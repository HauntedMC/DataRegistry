package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerNameHistoryServiceTest {

    @Test
    void constructorRejectsInvalidArguments() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        assertThrows(NullPointerException.class, () -> new PlayerNameHistoryService(null, logger, 32, true));
        assertThrows(NullPointerException.class, () -> new PlayerNameHistoryService(registry, null, 32, true));
        assertThrows(IllegalArgumentException.class, () -> new PlayerNameHistoryService(registry, logger, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new PlayerNameHistoryService(registry, logger, 33, true));
    }

    @Test
    void recordSeenUsernamePersistsNewHistoryWhenNoPriorEntryExists() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        @SuppressWarnings("unchecked")
        Query<PlayerNameHistoryEntity> historyQuery = mock(Query.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerEntity player = persistedPlayer();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(player);
        when(session.createQuery(anyString(), eq(PlayerNameHistoryEntity.class))).thenReturn(historyQuery);
        when(historyQuery.setParameter(anyString(), any())).thenReturn(historyQuery);
        when(historyQuery.setMaxResults(anyInt())).thenReturn(historyQuery);
        when(historyQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        service.recordSeenUsername(player, "  Alice  ");

        ArgumentCaptor<PlayerNameHistoryEntity> captor = ArgumentCaptor.forClass(PlayerNameHistoryEntity.class);
        verify(session).persist(captor.capture());
        PlayerNameHistoryEntity history = captor.getValue();
        assertEquals(player, history.getPlayer());
        assertEquals("Alice", history.getUsername());
        assertNotNull(history.getFirstSeenAt());
        assertNotNull(history.getLastSeenAt());
    }

    @Test
    void recordSeenUsernameUpdatesTimestampWhenUsernameDidNotChange() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        @SuppressWarnings("unchecked")
        Query<PlayerNameHistoryEntity> historyQuery = mock(Query.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerEntity player = persistedPlayer();
        PlayerNameHistoryEntity latest = new PlayerNameHistoryEntity();
        latest.setPlayer(player);
        latest.setUsername("Alice");
        latest.setFirstSeenAt(Instant.EPOCH);
        latest.setLastSeenAt(Instant.EPOCH);

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(player);
        when(session.createQuery(anyString(), eq(PlayerNameHistoryEntity.class))).thenReturn(historyQuery);
        when(historyQuery.setParameter(anyString(), any())).thenReturn(historyQuery);
        when(historyQuery.setMaxResults(anyInt())).thenReturn(historyQuery);
        when(historyQuery.uniqueResultOptional()).thenReturn(Optional.of(latest));

        service.recordSeenUsername(player, "Alice");

        verify(session, never()).persist(any());
        assertNotNull(latest.getLastSeenAt());
        assertEquals(true, latest.getLastSeenAt().isAfter(Instant.EPOCH));
    }

    @Test
    void recordSeenUsernameSkipsWhenFeatureDisabledOrPlayerIsInvalid() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerNameHistoryService disabledService = new PlayerNameHistoryService(registry, logger, 32, false);
        PlayerNameHistoryService enabledService = new PlayerNameHistoryService(registry, logger, 32, true);

        disabledService.recordSeenUsername(persistedPlayer(), "Alice");
        enabledService.recordSeenUsername(new PlayerEntity(), "Alice");

        verify(registry, never()).getORM();
        verify(logger).warn("recordSeenUsername called with an invalid player entity.");
    }

    @Test
    void recordSeenUsernameLogsRuntimeFailuresWithSanitizedUuid() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerEntity player = persistedPlayer();
        player.setUuid("uuid\nvalue");

        when(registry.getORM()).thenReturn(ormContext);
        doThrow(new RuntimeException("tx failed")).when(ormContext).runInTransaction(any());

        service.recordSeenUsername(player, "Alice");

        verify(logger).error(contains("uuid_value"), any(RuntimeException.class));
    }

    private static PlayerEntity persistedPlayer() {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setUuid("55f8e8cd-73be-44e5-8af6-a4f05248d6fb");
        player.setUsername("Alice");
        return player;
    }
}
