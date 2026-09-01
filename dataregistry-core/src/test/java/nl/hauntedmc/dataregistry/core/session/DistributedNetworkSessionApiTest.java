package nl.hauntedmc.dataregistry.core.session;

import nl.hauntedmc.dataprovider.database.coordination.CoordinationDataAccess;
import nl.hauntedmc.dataprovider.database.coordination.FencedLease;
import nl.hauntedmc.dataprovider.database.coordination.LeaseClaim;
import nl.hauntedmc.dataprovider.database.keyvalue.KeyValueDataAccess;
import nl.hauntedmc.dataprovider.database.keyvalue.KeyValueDatabaseProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedNetworkSessionApiTest {

    @Test
    void serializesConcurrentMutationsWithoutLosingSessionFields() {
        KeyValueDatabaseProvider provider = mock(KeyValueDatabaseProvider.class);
        KeyValueDataAccess values = mock(KeyValueDataAccess.class);
        CoordinationDataAccess coordination = mock(CoordinationDataAccess.class);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataAccess()).thenReturn(values);
        when(provider.getCoordinationDataAccess()).thenReturn(coordination);

        String proxyId = "proxy-a";
        UUID processEpoch = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        Duration ttl = Duration.ofSeconds(15);
        FencedLease initial = lease(playerUuid, proxyId, processEpoch, 1, ttl);
        FencedLease firstRenewed = lease(playerUuid, proxyId, processEpoch, 1, ttl);
        FencedLease secondRenewed = lease(playerUuid, proxyId, processEpoch, 1, ttl);
        CompletableFuture<Optional<FencedLease>> delayedRenew = new CompletableFuture<>();

        when(coordination.claim(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(
                new LeaseClaim(initial, Optional.empty(), 0)));
        when(coordination.writeFencedIndexed(any(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
        AtomicInteger renewals = new AtomicInteger();
        when(coordination.renew(any(), any())).thenAnswer(ignored -> renewals.getAndIncrement() == 0
                ? delayedRenew : CompletableFuture.completedFuture(Optional.of(secondRenewed)));

        DistributedNetworkSessionApi directory = new DistributedNetworkSessionApi(
                provider, "test-network", proxyId, processEpoch, ttl,
                Duration.ofMillis(500), Duration.ofSeconds(10));
        var claim = directory.claimOwnership(playerUuid, 769).toCompletableFuture().join();
        var opened = directory.open(claim, 42L, "Player").toCompletableFuture().join();

        var backendUpdate = directory.changeBackend(playerUuid, opened.fence(), "survival").toCompletableFuture();
        var routeUpdate = directory.updateLogicalRoute(playerUuid, opened.fence(), "survival", "games")
                .toCompletableFuture();

        verify(coordination, times(1)).renew(any(), any());
        delayedRenew.complete(Optional.of(firstRenewed));

        assertTrue(backendUpdate.join());
        assertTrue(routeUpdate.join());
        verify(coordination, times(2)).renew(any(), any());
        var current = directory.locallyOwnedSessions().iterator().next();
        assertEquals(Optional.of("survival"), current.currentBackend());
        assertEquals(Optional.of("survival"), current.logicalDestination());
        assertEquals(Optional.of("games"), current.logicalGroup());
        assertEquals(3L, current.revision());
    }

    private static FencedLease lease(
            UUID playerUuid, String proxyId, UUID processEpoch, long token, Duration ttl
    ) {
        return new FencedLease("dataregistry/session/player/" + playerUuid,
                proxyId + "/" + processEpoch, token, Instant.now().plus(ttl));
    }
}
