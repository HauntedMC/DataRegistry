package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityInitializationTracker;
import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityInitializationTracker.PlayerIdentityInitialization;
import nl.hauntedmc.dataregistry.backend.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
        PlayerIdentityInitializationTracker initialization = new PlayerIdentityInitializationTracker();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        assertThrows(NullPointerException.class, () -> new PlayerService(null, initialization, logger));
        assertThrows(NullPointerException.class, () -> new PlayerService(repository, null, logger));
        assertThrows(NullPointerException.class, () -> new PlayerService(repository, initialization, null));
    }

    @Test
    void onPlayerJoinDelegatesToRepositoryAndLogs() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, new PlayerIdentityInitializationTracker(), logger);
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
        PlayerService service = new PlayerService(
                mock(PlayerRepository.class),
                new PlayerIdentityInitializationTracker(),
                mock(ILoggerAdapter.class)
        );
        assertThrows(IllegalArgumentException.class, () -> service.onPlayerJoin(null));
    }

    @Test
    void onPlayerQuitRemovesFromCacheAndLogs() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, new PlayerIdentityInitializationTracker(), logger);

        service.onPlayerQuit("Alice\r", "uuid-123");

        verify(repository).removeActivePlayer("uuid-123");
        verify(logger).info(contains("Alice_"));
    }

    @Test
    void getActivePlayerDelegatesToRepository() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, new PlayerIdentityInitializationTracker(), logger);
        PlayerEntity player = new PlayerEntity();
        when(repository.getActivePlayer("uuid-123")).thenReturn(Optional.of(player));

        Optional<PlayerEntity> result = service.getActivePlayer("uuid-123");

        assertEquals(Optional.of(player), result);
        verify(repository).getActivePlayer(eq("uuid-123"));
    }

    @Test
    void getActivePlayerReturnsEmptyWhenRepositoryReturnsNullOptional() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, new PlayerIdentityInitializationTracker(), logger);
        when(repository.getActivePlayer("uuid-123")).thenReturn(null);

        Optional<PlayerEntity> result = service.getActivePlayer("uuid-123");

        assertEquals(Optional.empty(), result);
        verify(repository).getActivePlayer("uuid-123");
    }

    @Test
    void findKnownUsernamePrefersActiveCacheThenPersistence() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService service = new PlayerService(repository, new PlayerIdentityInitializationTracker(), logger);
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
        PlayerService service = new PlayerService(repository, new PlayerIdentityInitializationTracker(), logger);
        PlayerEntity persisted = new PlayerEntity();
        persisted.setUsername("PersistedName");

        when(repository.getActivePlayer("uuid-123")).thenReturn(Optional.empty());
        when(repository.findByUUID("uuid-123")).thenReturn(Optional.of(persisted));

        assertEquals(Optional.of("PersistedName"), service.findKnownUsername("uuid-123"));
        verify(repository).findByUUID("uuid-123");
    }

    @Test
    void completeIdentityInitializationPublishesPersistentIdentity() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        PlayerIdentityInitializationTracker initialization = new PlayerIdentityInitializationTracker();
        PlayerService service = new PlayerService(repository, initialization, mock(ILoggerAdapter.class));
        UUID uuid = UUID.randomUUID();
        PlayerEntity player = new PlayerEntity();
        player.setId(7L);
        player.setUuid(uuid.toString());
        player.setUsername("Alice");
        PlayerIdentityInitialization handle = service.beginIdentityInitialization(uuid);
        CompletableFuture<Optional<PlayerIdentity>> future = handle.future();

        service.completeIdentityInitialization(handle, player);

        assertEquals(Optional.of(7L), future.get().map(identity -> identity.playerId()));
    }

    @Test
    void completeIdentityInitializationFailsWhenPersistentIdentityIsInvalid() {
        PlayerRepository repository = mock(PlayerRepository.class);
        PlayerIdentityInitializationTracker initialization = new PlayerIdentityInitializationTracker();
        PlayerService service = new PlayerService(repository, initialization, mock(ILoggerAdapter.class));
        UUID uuid = UUID.randomUUID();
        PlayerEntity player = new PlayerEntity();
        player.setUuid(uuid.toString());
        player.setUsername("Alice");
        PlayerIdentityInitialization handle = service.beginIdentityInitialization(uuid);
        CompletableFuture<Optional<PlayerIdentity>> future = handle.future();

        service.completeIdentityInitialization(handle, player);

        assertThrows(ExecutionException.class, future::get);
    }
}
