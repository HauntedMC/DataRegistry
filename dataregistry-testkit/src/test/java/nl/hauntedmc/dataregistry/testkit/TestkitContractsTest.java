package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestkitContractsTest {

    @Test
    void temporaryPlayerIdsStartPositiveAndIncreaseWithoutDuplicates() {
        TemporaryPlayerIds ids = new TemporaryPlayerIds();

        assertEquals(
                LongStream.rangeClosed(1L, 1_000L).boxed().toList(),
                LongStream.generate(ids::next).limit(1_000L).boxed().toList()
        );
    }

    @Test
    void failureSimulationPreservesExactFailureCause() {
        RuntimeException failure = new RuntimeException("simulated");

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> FailureSimulation.failedStage(failure).toCompletableFuture().join()
        );

        assertSame(failure, exception.getCause());
    }

    @Test
    void playerFixturesAreStableAndUseUtf8ForNamedUuidGeneration() {
        String username = "Speler-Ünicode";
        PlayerIdentity first = PlayerFixtures.identity(7L, username);
        PlayerIdentity second = PlayerFixtures.identity(7L, username);
        UUID expected = UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8));

        assertEquals(first, second);
        assertEquals(expected, first.uuid());
        assertEquals(7L, first.playerId());
        assertEquals(username, first.username());
    }

    @Test
    void fakeApiExposesConfiguredCollaboratorsAndReadiness() {
        PlayerData players = mock(PlayerData.class);
        FeatureServiceDirectory services = mock(FeatureServiceDirectory.class);
        FakeDataRegistryApi api = new FakeDataRegistryApi(
                players,
                services,
                EnumSet.of(DataRegistryFeature.PLAYTIME, DataRegistryFeature.SESSIONS),
                true
        );

        assertSame(players, api.players());
        assertSame(services, api.featureServices());
        assertTrue(api.isReady());
        assertTrue(api.supports(DataRegistryFeature.PLAYTIME));
        assertFalse(api.supports(DataRegistryFeature.LANGUAGE));
    }

    @Test
    void fakeApiDefensivelyCopiesEnabledFeatures() {
        EnumSet<DataRegistryFeature> features = EnumSet.of(DataRegistryFeature.LANGUAGE);
        FakeDataRegistryApi api = new FakeDataRegistryApi(
                mock(PlayerData.class),
                mock(FeatureServiceDirectory.class),
                features,
                false
        );
        features.clear();

        assertEquals(Set.of(DataRegistryFeature.LANGUAGE), api.enabledFeatures());
        assertThrows(UnsupportedOperationException.class, () -> api.enabledFeatures().clear());
    }

    @Test
    void fakeApiRejectsMissingRequiredDependencies() {
        PlayerData players = mock(PlayerData.class);
        FeatureServiceDirectory services = mock(FeatureServiceDirectory.class);

        assertThrows(NullPointerException.class, () -> new FakeDataRegistryApi(null, services, Set.of(), false));
        assertThrows(NullPointerException.class, () -> new FakeDataRegistryApi(players, null, Set.of(), false));
        assertThrows(NullPointerException.class, () -> new FakeDataRegistryApi(players, services, null, false));
    }
}
