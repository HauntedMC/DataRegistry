package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerServiceTest {

    @Test
    void constructorRejectsNullDependencies() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        assertThrows(NullPointerException.class, () -> new PlayerService(null, logger));
        assertThrows(NullPointerException.class, () -> new PlayerService(repository, null));
    }

    @Test
    void onPlayerJoinDelegatesToRepositoryAndLogs() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, logger);
        PlayerEntity temporary = new PlayerEntity();
        temporary.setUuid("0f4f1f64-dcb1-49a2-bf6d-5ecf6f00d6da");
        temporary.setUsername("Alice\nName");
        PlayerEntity persistent = new PlayerEntity();
        persistent.setId(10L);
        persistent.setUuid(temporary.getUuid());
        persistent.setUsername("Alice");

        when(repository.getOrCreateActivePlayer(temporary.getUuid(), temporary.getUsername())).thenReturn(persistent);

        PlayerEntity result = service.onPlayerJoin(temporary);

        assertSame(persistent, result);
        verify(repository).getOrCreateActivePlayer(temporary.getUuid(), temporary.getUsername());
        verify(logger).info(contains("Alice_Name"));
    }

    @Test
    void onPlayerJoinRejectsNullEntity() {
        PlayerService service = new PlayerService(mock(PlayerRepository.class), mock(ILoggerAdapter.class));
        assertThrows(IllegalArgumentException.class, () -> service.onPlayerJoin(null));
    }

    @Test
    void onPlayerQuitRemovesFromCacheAndLogs() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, logger);

        service.onPlayerQuit("Alice\r", "uuid-123");

        verify(repository).removeActivePlayer("uuid-123");
        verify(logger).info(contains("Alice_"));
    }

    @Test
    void getActivePlayerDelegatesToRepository() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, logger);
        PlayerEntity player = new PlayerEntity();
        when(repository.getActivePlayer("uuid-123")).thenReturn(Optional.of(player));

        Optional<PlayerEntity> result = service.getActivePlayer("uuid-123");

        assertEquals(Optional.of(player), result);
        verify(repository).getActivePlayer(eq("uuid-123"));
    }

    @Test
    void findKnownUsernamePrefersActiveCacheThenPersistence() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, logger);
        PlayerEntity activePlayer = new PlayerEntity();
        activePlayer.setUsername("ActiveName");

        when(repository.getActivePlayer("uuid-123")).thenReturn(Optional.of(activePlayer));

        assertEquals(Optional.of("ActiveName"), service.findKnownUsername("uuid-123"));
        verify(repository).getActivePlayer("uuid-123");
        verify(repository, never()).findByUUID("uuid-123");
    }

    @Test
    void findKnownUsernameFallsBackToPersistentLookup() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, logger);
        PlayerEntity persisted = new PlayerEntity();
        persisted.setUsername("PersistedName");

        when(repository.getActivePlayer("uuid-123")).thenReturn(Optional.empty());
        when(repository.findByUUID("uuid-123")).thenReturn(Optional.of(persisted));

        assertEquals(Optional.of("PersistedName"), service.findKnownUsername("uuid-123"));
        verify(repository).findByUUID("uuid-123");
    }
}
