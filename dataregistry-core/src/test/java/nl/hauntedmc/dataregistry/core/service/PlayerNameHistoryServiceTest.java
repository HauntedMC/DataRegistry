package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
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
    void recordUsernameChangePersistsFormerNameWithLastDisconnectTimestamp() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerEntity player = persistedPlayer("Alice");
        PlayerConnectionInfoEntity connectionInfo = new PlayerConnectionInfoEntity();
        Instant lastDisconnectAt = Instant.now().minusSeconds(120);
        connectionInfo.setLastDisconnectAt(lastDisconnectAt);

        when(registry.getORM()).thenReturn(ormContext);
        when(registry.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)).thenReturn(true);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(player);
        when(session.find(PlayerConnectionInfoEntity.class, player.getId())).thenReturn(connectionInfo);

        service.recordUsernameChange(player, "OldName", "Alice");

        ArgumentCaptor<PlayerNameHistoryEntity> captor = ArgumentCaptor.forClass(PlayerNameHistoryEntity.class);
        verify(session).persist(captor.capture());
        PlayerNameHistoryEntity history = captor.getValue();
        assertSame(player, history.getPlayer());
        assertEquals("OldName", history.getUsername());
        assertEquals(lastDisconnectAt, history.getLastSeenAt());
    }

    @Test
    void recordUsernameChangeFallsBackToCurrentTimeWhenDisconnectTimestampUnavailable() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerEntity player = persistedPlayer("Alice");

        when(registry.getORM()).thenReturn(ormContext);
        when(registry.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)).thenReturn(true);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(player);
        when(session.find(PlayerConnectionInfoEntity.class, player.getId())).thenReturn(null);

        Instant before = Instant.now();
        service.recordUsernameChange(player, "OldName", "Alice");
        Instant after = Instant.now();

        ArgumentCaptor<PlayerNameHistoryEntity> captor = ArgumentCaptor.forClass(PlayerNameHistoryEntity.class);
        verify(session).persist(captor.capture());
        PlayerNameHistoryEntity history = captor.getValue();
        assertNotNull(history.getLastSeenAt());
        assertEquals(true, !history.getLastSeenAt().isBefore(before) && !history.getLastSeenAt().isAfter(after));
    }

    @Test
    void recordUsernameChangeSkipsWhenNoChangeOrInvalidInput() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);

        service.recordUsernameChange(persistedPlayer("Alice"), "Alice", "Alice");
        service.recordUsernameChange(persistedPlayer("Alice"), null, "Alice");
        service.recordUsernameChange(persistedPlayer("Alice"), "Alice", null);
        service.recordUsernameChange(new PlayerEntity(), "Alice", "Bob");

        verify(registry, never()).getORM();
        verify(logger).warn("recordUsernameChange called with an invalid player entity.");
    }

    @Test
    void recordUsernameChangeSkipsWhenFeatureDisabled() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, false);

        service.recordUsernameChange(persistedPlayer("Alice"), "OldName", "Alice");

        verify(registry, never()).getORM();
    }

    @Test
    void recordUsernameChangeLogsRuntimeFailuresWithSanitizedUuid() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerEntity player = persistedPlayer("Alice");
        player.setUuid("uuid\nvalue");

        when(registry.getORM()).thenReturn(ormContext);
        when(registry.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)).thenReturn(false);
        doThrow(new RuntimeException("tx failed")).when(ormContext).runInTransaction(any());

        service.recordUsernameChange(player, "OldName", "Alice");

        verify(logger).error(contains("uuid_value"), any(RuntimeException.class));
    }

    @Test
    void listChronologicalHistoryForCurrentUsernameResolvesByCurrentName() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerNameHistoryEntity> historyQuery = mock(Query.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerEntity player = persistedPlayer("Alice");
        PlayerNameHistoryEntity alpha = history("Alpha", Instant.EPOCH, player);
        PlayerNameHistoryEntity bravo = history("Bravo", Instant.EPOCH.plusSeconds(10), player);

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(startsWith("SELECT p"), eq(PlayerEntity.class))).thenReturn(playerQuery);
        when(playerQuery.setParameter("username", "alice")).thenReturn(playerQuery);
        when(playerQuery.setMaxResults(1)).thenReturn(playerQuery);
        when(playerQuery.uniqueResult()).thenReturn(player);
        when(session.createQuery(startsWith("SELECT h"), eq(PlayerNameHistoryEntity.class))).thenReturn(historyQuery);
        when(historyQuery.setParameter("playerId", 1L)).thenReturn(historyQuery);
        when(historyQuery.setMaxResults(5)).thenReturn(historyQuery);
        when(historyQuery.list()).thenReturn(List.of(alpha, bravo));

        List<PlayerNameHistoryService.NameHistoryView> result = service.listChronologicalHistoryForCurrentUsername("Alice", 5);

        assertEquals(2, result.size());
        assertEquals("Alpha", result.getFirst().formerUsername());
        assertEquals(Instant.EPOCH, result.getFirst().lastSeenAt());
        assertEquals("Bravo", result.get(1).formerUsername());
    }

    private static PlayerEntity persistedPlayer(String username) {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setUuid("55f8e8cd-73be-44e5-8af6-a4f05248d6fb");
        player.setUsername(username);
        return player;
    }

    private static PlayerNameHistoryEntity history(String username, Instant lastSeenAt, PlayerEntity player) {
        PlayerNameHistoryEntity history = new PlayerNameHistoryEntity();
        history.setPlayer(player);
        history.setUsername(username);
        history.setLastSeenAt(lastSeenAt);
        return history;
    }
}
