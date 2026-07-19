package nl.hauntedmc.dataregistry.api.player;

import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerDirectoryTest {

    @Test
    void whenReadyReturnsActiveIdentityImmediately() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerDirectory directory = new PlayerDirectory(repository);
        when(repository.getActiveIdentity(uuid.toString()))
                .thenReturn(Optional.of(new PlayerRepository.PlayerIdentity(10L, uuid.toString(), "Alice")));

        Optional<PlayerIdentity> identity = directory.whenReady(uuid).get();

        assertTrue(identity.isPresent());
        assertEquals(10L, identity.get().playerId());
        assertEquals(uuid, identity.get().uuid());
    }

    @Test
    void readinessFutureCompletesWhenIdentityIsMarkedReady() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerDirectory directory = new PlayerDirectory(repository);
        CompletableFuture<Optional<PlayerIdentity>> future = directory.beginIdentityInitialization(uuid);
        PlayerIdentity identity = new PlayerIdentity(12L, uuid, "Alice");

        directory.completeIdentityReady(identity);

        assertEquals(Optional.of(identity), future.get());
    }

    @Test
    void readinessFutureCompletesEmptyWhenPlayerLeavesEarly() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerDirectory directory = new PlayerDirectory(repository);
        CompletableFuture<Optional<PlayerIdentity>> future = directory.beginIdentityInitialization(uuid);

        directory.completeIdentityUnavailable(uuid);

        assertEquals(Optional.empty(), future.get());
    }

    @Test
    void readinessFutureFailsWhenInitializationFails() {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerDirectory directory = new PlayerDirectory(repository);
        CompletableFuture<Optional<PlayerIdentity>> future = directory.beginIdentityInitialization(uuid);

        directory.failIdentityInitialization(uuid, new IllegalStateException("database down"));

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    void shutdownFailsOutstandingReadinessFutures() {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerDirectory directory = new PlayerDirectory(repository);
        CompletableFuture<Optional<PlayerIdentity>> future = directory.beginIdentityInitialization(uuid);

        directory.shutdown();

        assertThrows(ExecutionException.class, future::get);
    }
}
