package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerConnectionSnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageSettings;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePlayerDataTest {

    @Test
    void explicitPlayerIdIdentifierMatchesRuntimeSyntax() {
        FakePlayerData players = new FakePlayerData();
        PlayerIdentity identity = PlayerFixtures.identity(42L, "Alice");
        players.putIdentity(identity);

        assertEquals(identity, players.findIdentityByIdentifier("#42").toCompletableFuture().join().orElseThrow());
        assertEquals(42L, players.findPlayerIdByIdentifier(" #42 ").toCompletableFuture().join().orElseThrow());
        assertEquals(identity, players.findProfileByIdentifier("#42", 0)
                .toCompletableFuture().join().orElseThrow().identity());
    }

    @Test
    void uuidReadsRespectDisabledFeatureSetEvenWhenStateWasSeeded() {
        FakePlayerData players = new FakePlayerData(Set.of());
        PlayerIdentity identity = PlayerFixtures.identity(1L, "Alice");
        players.putIdentity(identity)
                .putLanguage(new PlayerLanguageSettings(1L, "AUTO", "NL"))
                .putNickname(1L, "Ali")
                .putConnection(new PlayerConnectionSnapshot(
                        1L,
                        "127.0.0.1",
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        "play.example.test"
                ));

        assertTrue(players.findLanguage(identity.uuid()).toCompletableFuture().join().isEmpty());
        assertTrue(players.findNickname(identity.uuid()).toCompletableFuture().join().isEmpty());
        assertTrue(players.findConnection(identity.uuid()).toCompletableFuture().join().isEmpty());
    }

    @Test
    void clearRemovesSeededStateButKeepsFeatureCapabilities() {
        FakePlayerData players = new FakePlayerData(Set.of(DataRegistryFeature.LANGUAGE));
        PlayerIdentity identity = PlayerFixtures.identity(1L, "Alice");
        players.putActiveIdentity(identity)
                .putLanguage(new PlayerLanguageSettings(1L, "AUTO", "NL"));

        players.clear();

        assertTrue(players.findIdentity(identity.uuid()).toCompletableFuture().join().isEmpty());
        assertTrue(players.findLanguage(1L).toCompletableFuture().join().isEmpty());
        assertTrue(players.snapshotActiveIdentities().isEmpty());
        assertTrue(players.supports(DataRegistryFeature.LANGUAGE));
        assertFalse(players.supports(DataRegistryFeature.PLAYTIME));
    }
}
