package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerNameHistoryRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlayerNameHistoryRepository historyRepository = mock(PlayerNameHistoryRepository.class);
        PlayerNameHistoryService service = new PlayerNameHistoryService(registry, logger, 32, true);
        PlayerEntity player = persistedPlayer("Alice");
        PlayerNameHistoryEntity first = new PlayerNameHistoryEntity();
        first.setPlayer(player);
        first.setUsername("Alpha");
        first.setLastSeenAt(Instant.EPOCH);
        PlayerNameHistoryEntity second = new PlayerNameHistoryEntity();
        second.setPlayer(player);
        second.setUsername("Bravo");
        second.setLastSeenAt(Instant.EPOCH.plusSeconds(10));

        when(registry.getPlayerRepository()).thenReturn(playerRepository);
        when(registry.getPlayerNameHistoryRepository()).thenReturn(historyRepository);
        when(playerRepository.findByUsername("Alice")).thenReturn(Optional.of(player));
        when(historyRepository.findChronologicalByPlayer(player.getId(), 5)).thenReturn(List.of(first, second));

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
}
