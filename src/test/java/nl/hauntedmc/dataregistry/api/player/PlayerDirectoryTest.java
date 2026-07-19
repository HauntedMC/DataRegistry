package nl.hauntedmc.dataregistry.api.player;

import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityInitializationTracker;
import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityInitializationTracker.PlayerIdentityInitialization;
import nl.hauntedmc.dataregistry.backend.player.RepositoryPlayerDirectory;
import nl.hauntedmc.dataregistry.backend.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerDirectoryTest {

    @Test
    void whenReadyReturnsActiveIdentityImmediately() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerDirectory directory = new RepositoryPlayerDirectory(repository, new PlayerIdentityInitializationTracker());
        when(repository.getActiveIdentity(uuid.toString()))
                .thenReturn(Optional.of(new PlayerIdentity(10L, uuid, "Alice")));

        Optional<PlayerIdentity> identity = directory.whenReady(uuid).get();

        assertTrue(identity.isPresent());
        assertEquals(10L, identity.get().playerId());
        assertEquals(uuid, identity.get().uuid());
    }

    @Test
    void getActiveIdentityAcceptsUuidString() {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(10L, uuid, "Alice");
        PlayerDirectory directory = new RepositoryPlayerDirectory(repository, new PlayerIdentityInitializationTracker());
        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.of(identity));

        assertEquals(Optional.of(identity), directory.getActiveIdentity(uuid.toString()));
    }

    @Test
    void initializationFutureCompletesWhenIdentityIsMarkedReady() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker initialization = new PlayerIdentityInitializationTracker();
        PlayerDirectory directory = new RepositoryPlayerDirectory(repository, initialization);
        PlayerIdentityInitialization handle = initialization.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> future = handle.future();
        PlayerIdentity identity = new PlayerIdentity(12L, uuid, "Alice");

        initialization.complete(handle, identity);

        assertEquals(Optional.of(identity), future.get());
        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.of(identity));
        assertEquals(Optional.of(identity), directory.whenReady(uuid).get());
    }

    @Test
    void initializationFutureCompletesEmptyWhenPlayerLeavesEarly() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker initialization = new PlayerIdentityInitializationTracker();
        PlayerIdentityInitialization handle = initialization.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> future = handle.future();

        initialization.completeUnavailable(handle);

        assertEquals(Optional.empty(), future.get());
    }

    @Test
    void initializationFutureFailsWhenInitializationFails() {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker initialization = new PlayerIdentityInitializationTracker();
        PlayerIdentityInitialization handle = initialization.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> future = handle.future();

        initialization.fail(handle, new IllegalStateException("database down"));

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    void shutdownFailsOutstandingInitializationFutures() {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker initialization = new PlayerIdentityInitializationTracker();
        PlayerIdentityInitialization handle = initialization.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> future = handle.future();

        initialization.shutdown();

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    void staleInitializationHandleCannotCompleteReconnectFuture() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker initialization = new PlayerIdentityInitializationTracker();
        PlayerDirectory directory = new RepositoryPlayerDirectory(repository, initialization);
        PlayerIdentityInitialization firstJoin = initialization.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> firstFuture = firstJoin.future();
        PlayerIdentityInitialization secondJoin = initialization.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> secondFuture = directory.whenReady(uuid);

        initialization.complete(firstJoin, new PlayerIdentity(12L, uuid, "Alice"));

        assertEquals(Optional.empty(), firstFuture.get());
        assertFalse(secondFuture.isDone());
        initialization.complete(secondJoin, new PlayerIdentity(13L, uuid, "Alice"));
        assertEquals(Optional.of(13L), secondFuture.get().map(PlayerIdentity::playerId));
    }

    @Test
    void findByUuidStringRejectsInvalidUuidWithoutQueryingPersistence() {
        PlayerRepository repository = mock(PlayerRepository.class);
        PlayerDirectory directory = new RepositoryPlayerDirectory(repository, new PlayerIdentityInitializationTracker());

        assertEquals(Optional.empty(), directory.findByUuid("not-a-uuid"));
    }
}
