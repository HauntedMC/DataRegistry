package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageSettings;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.player.PlayerPageRequest;
import nl.hauntedmc.dataregistry.api.population.PopulationData;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    void fakePlayerDataSupportsLookupPagingMutablePreferencesAndProfiles() {
        FakePlayerData players = new FakePlayerData(EnumSet.of(
                DataRegistryFeature.LANGUAGE,
                DataRegistryFeature.NICKNAMES
        ));
        PlayerIdentity alice = PlayerFixtures.identity(1L, "Alice");
        PlayerIdentity alex = PlayerFixtures.identity(2L, "Alex");
        PlayerIdentity bob = PlayerFixtures.identity(3L, "Bob");
        players.putActiveIdentity(alice)
                .putIdentity(alex)
                .putIdentity(bob)
                .putLanguage(new PlayerLanguageSettings(1L, "AUTO", "NL"))
                .putNickname(1L, "Ali");

        assertEquals(alice, players.whenReady(alice.uuid()).join().orElseThrow());
        assertEquals(alice, players.findIdentity(PlayerLookup.username("alice"))
                .toCompletableFuture().join().orElseThrow());
        assertEquals(List.of(alex, alice), players.findIdentitiesByUsernamePrefix("al", 10)
                .toCompletableFuture().join());

        var firstPage = players.findIdentitiesByUsernamePrefix("a", PlayerPageRequest.firstPage(1))
                .toCompletableFuture().join();
        var secondPage = players.findIdentitiesByUsernamePrefix(
                "a",
                new PlayerPageRequest(firstPage.nextCursor().orElseThrow(), 1)
        ).toCompletableFuture().join();
        assertEquals(List.of(alex), firstPage.items());
        assertEquals(List.of(alice), secondPage.items());

        var profile = players.findProfile(alice.playerId(), 5).toCompletableFuture().join().orElseThrow();
        assertEquals("Ali", profile.nickname().orElseThrow());
        assertEquals("NL", profile.language().orElseThrow().effectiveLanguage());

        players.saveNickname(alice.playerId(), "AliceTheGreat").toCompletableFuture().join();
        assertEquals("AliceTheGreat", players.findNickname(alice.playerId()).toCompletableFuture().join().orElseThrow());
        players.clearNickname(alice.playerId()).toCompletableFuture().join();
        assertTrue(players.findNickname(alice.playerId()).toCompletableFuture().join().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> new FakePlayerData(Set.of()).saveNickname(1L, "nope")
        );
    }

    @Test
    void fakeFeatureServicesHonorOwnershipReplacementAndHandleIdentity() {
        FakeFeatureServiceDirectory services = new FakeFeatureServiceDirectory();
        Runnable first = () -> { };
        Runnable replacement = () -> { };

        FeatureServiceHandle oldHandle = services.register("ServerFeatures", "Vanish", Runnable.class, first);
        assertSame(first, services.require(Runnable.class));
        assertEquals("ServerFeatures", services.describe(Runnable.class).orElseThrow().ownerPlugin());

        FeatureServiceHandle replacementHandle = services.register(
                "ServerFeatures",
                "Vanish",
                Runnable.class,
                replacement
        );
        oldHandle.close();
        assertSame(replacement, services.require(Runnable.class));
        assertThrows(
                IllegalStateException.class,
                () -> services.register("ProxyFeatures", "Other", Runnable.class, first)
        );

        replacementHandle.close();
        assertFalse(services.contains(Runnable.class));
    }

    @Test
    void fakeApiBuilderUsesInMemoryCollaboratorsAndConfiguredFeatures() {
        FakeDataRegistryApi api = FakeDataRegistryApi.builder()
                .enable(DataRegistryFeature.LANGUAGE, DataRegistryFeature.POPULATION)
                .ready(true)
                .build();

        assertInstanceOf(FakePlayerData.class, api.players());
        assertInstanceOf(FakePopulationData.class, api.population());
        assertInstanceOf(FakeFeatureServiceDirectory.class, api.featureServices());
        assertTrue(api.isReady());
        assertTrue(api.supports(DataRegistryFeature.LANGUAGE));
        assertTrue(api.supports(DataRegistryFeature.POPULATION));
        assertFalse(api.supports(DataRegistryFeature.PLAYTIME));
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
