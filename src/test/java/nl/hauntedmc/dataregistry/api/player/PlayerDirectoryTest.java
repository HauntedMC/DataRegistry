package nl.hauntedmc.dataregistry.api.player;

import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityReadiness;
import nl.hauntedmc.dataregistry.backend.player.DefaultPlayerDirectory;
import nl.hauntedmc.dataregistry.backend.repository.PlayerRepository;
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
        PlayerDirectory directory = new DefaultPlayerDirectory(repository, new PlayerIdentityReadiness());
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
        PlayerDirectory directory = new DefaultPlayerDirectory(repository, new PlayerIdentityReadiness());
        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.of(identity));

        assertEquals(Optional.of(identity), directory.getActiveIdentity(uuid.toString()));
    }

    @Test
    void readinessFutureCompletesWhenIdentityIsMarkedReady() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        PlayerDirectory directory = new DefaultPlayerDirectory(repository, readiness);
        CompletableFuture<Optional<PlayerIdentity>> future = readiness.begin(uuid);
        PlayerIdentity identity = new PlayerIdentity(12L, uuid, "Alice");

        readiness.complete(identity);

        assertEquals(Optional.of(identity), future.get());
        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.of(identity));
        assertEquals(Optional.of(identity), directory.whenReady(uuid).get());
    }

    @Test
    void readinessFutureCompletesEmptyWhenPlayerLeavesEarly() throws Exception {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        CompletableFuture<Optional<PlayerIdentity>> future = readiness.begin(uuid);

        readiness.completeUnavailable(uuid);

        assertEquals(Optional.empty(), future.get());
    }

    @Test
    void readinessFutureFailsWhenInitializationFails() {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        CompletableFuture<Optional<PlayerIdentity>> future = readiness.begin(uuid);

        readiness.fail(uuid, new IllegalStateException("database down"));

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    void shutdownFailsOutstandingReadinessFutures() {
        PlayerRepository repository = mock(PlayerRepository.class);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        CompletableFuture<Optional<PlayerIdentity>> future = readiness.begin(uuid);

        readiness.shutdown();

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    void findByUuidStringRejectsInvalidUuidWithoutQueryingPersistence() {
        PlayerRepository repository = mock(PlayerRepository.class);
        PlayerDirectory directory = new DefaultPlayerDirectory(repository, new PlayerIdentityReadiness());

        assertEquals(Optional.empty(), directory.findByUuid("not-a-uuid"));
    }
}
