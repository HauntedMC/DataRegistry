package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.player.PlayerPage;
import nl.hauntedmc.dataregistry.api.player.PlayerPageRequest;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerIdentityInitializationTracker;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryPlayerDirectoryTest {

    @Test
    void constructorRejectsMissingDependencies() {
        PlayerRepository repository = mock(PlayerRepository.class);
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        DataRegistryQueryExecutor executor = DataRegistryQueryExecutor.immediateForTesting();

        assertThrows(NullPointerException.class, () -> new RepositoryPlayerDirectory(null, tracker, executor));
        assertThrows(NullPointerException.class, () -> new RepositoryPlayerDirectory(repository, null, executor));
        assertThrows(NullPointerException.class, () -> new RepositoryPlayerDirectory(repository, tracker, null));
    }

    @Test
    void cachedUuidLookupsNormalizeStringsAndShortCircuitInvalidValues() {
        PlayerRepository repository = mock(PlayerRepository.class);
        RepositoryPlayerDirectory directory = directory(repository);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = identity(uuid, 1L);
        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.of(identity));

        assertEquals(Optional.of(identity), directory.findActiveIdentityCached(uuid));
        assertEquals(Optional.of(identity), directory.findActiveIdentityCached("  " + uuid + "  "));
        assertTrue(directory.findActiveIdentityCached((UUID) null).isEmpty());
        assertTrue(directory.findActiveIdentityCached((String) null).isEmpty());
        assertTrue(directory.findActiveIdentityCached(" ").isEmpty());
        assertTrue(directory.findActiveIdentityCached("bad-uuid").isEmpty());

        verify(repository, times(2)).getActiveIdentity(uuid.toString());
        verify(repository, never()).getActiveIdentity("bad-uuid");
    }

    @Test
    void typedAndBulkLookupsDelegateWithoutChangingResults() {
        PlayerRepository repository = mock(PlayerRepository.class);
        RepositoryPlayerDirectory directory = directory(repository);
        PlayerLookup lookup = PlayerLookup.username("Alice");
        PlayerIdentity identity = identity(UUID.randomUUID(), 2L);
        Map<PlayerLookup, Optional<PlayerIdentity>> bulk = Map.of(lookup, Optional.of(identity));
        when(repository.findIdentity(lookup)).thenReturn(Optional.of(identity));
        when(repository.findIdentities(List.of(lookup))).thenReturn(bulk);

        assertEquals(Optional.of(identity), directory.findIdentity(lookup).toCompletableFuture().join());
        assertSame(bulk, directory.findIdentities(List.of(lookup)).toCompletableFuture().join());
    }

    @Test
    void uuidLookupsShortCircuitInvalidInputAndDelegateCanonicalValues() {
        PlayerRepository repository = mock(PlayerRepository.class);
        RepositoryPlayerDirectory directory = directory(repository);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = identity(uuid, 3L);
        when(repository.findIdentityByUUID(uuid.toString())).thenReturn(Optional.of(identity));

        assertEquals(Optional.of(identity), directory.findByUuid(uuid).toCompletableFuture().join());
        assertEquals(Optional.of(identity), directory.findByUuid("  " + uuid + "  ").toCompletableFuture().join());
        assertTrue(directory.findByUuid((UUID) null).toCompletableFuture().join().isEmpty());
        assertTrue(directory.findByUuid("bad").toCompletableFuture().join().isEmpty());

        verify(repository, never()).findIdentityByUUID("bad");
    }

    @Test
    void namedIdentifierAndIdLookupsDelegateToTheirSpecificRepositoryMethods() {
        PlayerRepository repository = mock(PlayerRepository.class);
        RepositoryPlayerDirectory directory = directory(repository);
        PlayerIdentity identity = identity(UUID.randomUUID(), 4L);
        when(repository.findIdentityById(4L)).thenReturn(Optional.of(identity));
        when(repository.findIdentityByUsername("Alice")).thenReturn(Optional.of(identity));
        when(repository.findIdentityByUsernameIgnoreCase("alice")).thenReturn(Optional.of(identity));
        when(repository.findIdentityByIdentifier("Alice")).thenReturn(Optional.of(identity));

        assertEquals(Optional.of(identity), directory.findByPlayerId(4L).toCompletableFuture().join());
        assertEquals(Optional.of(identity), directory.findByUsername("Alice").toCompletableFuture().join());
        assertEquals(Optional.of(identity), directory.findByUsernameIgnoreCase("alice").toCompletableFuture().join());
        assertEquals(Optional.of(identity), directory.findByIdentifier("Alice").toCompletableFuture().join());
    }

    @Test
    void prefixLookupForwardsLegacyAndPagedRequests() {
        PlayerRepository repository = mock(PlayerRepository.class);
        RepositoryPlayerDirectory directory = directory(repository);
        PlayerIdentity identity = identity(UUID.randomUUID(), 5L);
        PlayerPageRequest request = new PlayerPageRequest("cursor", 10);
        PlayerPage<PlayerIdentity> page = new PlayerPage<>(List.of(identity), Optional.of("next"));
        when(repository.findIdentitiesByUsernamePrefix("Al", 5)).thenReturn(List.of(identity));
        when(repository.findIdentitiesByUsernamePrefix("Al", request)).thenReturn(page);

        assertEquals(List.of(identity), directory.findByUsernamePrefix("Al", 5).toCompletableFuture().join());
        assertSame(page, directory.findByUsernamePrefix("Al", request).toCompletableFuture().join());
    }

    @Test
    void repositoryFailuresRemainExceptionalStages() {
        PlayerRepository repository = mock(PlayerRepository.class);
        RepositoryPlayerDirectory directory = directory(repository);
        RuntimeException failure = new RuntimeException("database failed");
        when(repository.findIdentityByUsernameIgnoreCase("Alice")).thenThrow(failure);

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> directory.findByUsernameIgnoreCase("Alice").toCompletableFuture().join()
        );

        assertEquals(failure, exception.getCause());
    }

    @Test
    void activeSnapshotIsReturnedDirectlyFromRepository() {
        PlayerRepository repository = mock(PlayerRepository.class);
        RepositoryPlayerDirectory directory = directory(repository);
        PlayerIdentity identity = identity(UUID.randomUUID(), 6L);
        Map<String, PlayerIdentity> snapshot = Map.of(identity.uuid().toString(), identity);
        when(repository.snapshotActiveIdentities()).thenReturn(snapshot);

        assertSame(snapshot, directory.snapshotActiveIdentities());
    }

    @Test
    void readinessUsesActiveCacheBeforeWaitingForPendingInitialization() {
        PlayerRepository repository = mock(PlayerRepository.class);
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        RepositoryPlayerDirectory directory = new RepositoryPlayerDirectory(repository, tracker);
        UUID uuid = UUID.randomUUID();
        PlayerIdentity active = identity(uuid, 7L);
        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.of(active));

        assertEquals(Optional.of(active), directory.whenReady(uuid).join());
        assertEquals(Optional.of(active), directory.whenReady(uuid.toString()).join());
    }

    @Test
    void readinessWaitsForMatchingInitializationAndRejectsInvalidUuidString() {
        PlayerRepository repository = mock(PlayerRepository.class);
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        RepositoryPlayerDirectory directory = new RepositoryPlayerDirectory(repository, tracker);
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization initialization = tracker.begin(uuid);
        PlayerIdentity identity = identity(uuid, 8L);
        when(repository.getActiveIdentity(uuid.toString())).thenReturn(Optional.empty());

        var waiting = directory.whenReady(uuid);
        assertTrue(directory.whenReady("invalid").join().isEmpty());
        tracker.complete(initialization, identity);

        assertEquals(Optional.of(identity), waiting.join());
    }

    private static RepositoryPlayerDirectory directory(PlayerRepository repository) {
        return new RepositoryPlayerDirectory(repository, new PlayerIdentityInitializationTracker());
    }

    private static PlayerIdentity identity(UUID uuid, long id) {
        return new PlayerIdentity(id, uuid, "Player" + id);
    }
}
