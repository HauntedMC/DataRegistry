package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.population.PopulationData;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    void temporaryPlayerIdsRemainUniqueAcrossConcurrentCallers() throws InterruptedException {
        TemporaryPlayerIds ids = new TemporaryPlayerIds();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Callable<Long>> calls = LongStream.range(0L, 2_000L)
                .mapToObj(ignored -> (Callable<Long>) ids::next)
                .toList();

        try {
            List<Long> generated = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError("Temporary id generation failed", exception);
                        }
                    })
                    .toList();

            assertEquals(2_000, generated.size());
            assertEquals(2_000, new HashSet<>(generated).size());
            assertTrue(generated.stream().allMatch(id -> id > 0L));
        } finally {
            executor.shutdownNow();
        }
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
        PopulationData population = new FakePopulationData();
        FeatureServiceDirectory services = mock(FeatureServiceDirectory.class);
        FakeDataRegistryApi api = new FakeDataRegistryApi(
                players,
                population,
                services,
                EnumSet.of(DataRegistryFeature.POPULATION, DataRegistryFeature.PLAYTIME, DataRegistryFeature.SESSIONS),
                true
        );

        assertSame(players, api.players());
        assertSame(population, api.population());
        assertSame(services, api.featureServices());
        assertTrue(api.isReady());
        assertTrue(api.supports(DataRegistryFeature.POPULATION));
        assertTrue(api.supports(DataRegistryFeature.PLAYTIME));
        assertFalse(api.supports(DataRegistryFeature.LANGUAGE));
    }

    @Test
    void fakeApiDefensivelyCopiesEnabledFeatures() {
        EnumSet<DataRegistryFeature> features = EnumSet.of(DataRegistryFeature.LANGUAGE);
        FakeDataRegistryApi api = new FakeDataRegistryApi(
                mock(PlayerData.class),
                new FakePopulationData(),
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
        PopulationData population = new FakePopulationData();
        FeatureServiceDirectory services = mock(FeatureServiceDirectory.class);

        assertThrows(
                NullPointerException.class,
                () -> new FakeDataRegistryApi(null, population, services, Set.of(), false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new FakeDataRegistryApi(players, null, services, Set.of(), false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new FakeDataRegistryApi(players, population, null, Set.of(), false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new FakeDataRegistryApi(players, population, services, null, false)
        );
    }
}
