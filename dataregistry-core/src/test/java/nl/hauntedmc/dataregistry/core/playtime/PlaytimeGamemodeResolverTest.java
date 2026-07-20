package nl.hauntedmc.dataregistry.core.playtime;

import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeGamemodeResolverTest {

    @Test
    void resolveMatchesOrderedGlobRulesAndAppliesFlags() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .ignoredGamemodes(Set.of("queue"))
                .excludedFromNetworkTotalGamemodes(Set.of("lobby"))
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("lobby-*", "lobby"),
                        new PlaytimeTrackingSettings.ServerGamemodeRule("queue-?", "queue")
                ))
                .build();
        PlaytimeGamemodeResolver resolver = new PlaytimeGamemodeResolver(settings);

        PlaytimeGamemodeResolver.ResolvedGamemode lobby = resolver.resolve("Lobby-2");
        PlaytimeGamemodeResolver.ResolvedGamemode queue = resolver.resolve("queue-1");

        assertEquals("lobby-2", lobby.serverName());
        assertEquals("lobby", lobby.gamemodeKey());
        assertTrue(lobby.tracked());
        assertFalse(lobby.countedTowardsNetworkTotal());

        assertEquals("queue", queue.gamemodeKey());
        assertFalse(queue.tracked());
        assertFalse(queue.countedTowardsNetworkTotal());
    }

    @Test
    void resolveFallsBackToNormalizedServerNameWhenEnabled() {
        PlaytimeGamemodeResolver resolver = new PlaytimeGamemodeResolver(
                PlaytimeTrackingSettings.defaults()
        );

        PlaytimeGamemodeResolver.ResolvedGamemode resolved = resolver.resolve(" SkyBlock-01 ");

        assertEquals("skyblock-01", resolved.serverName());
        assertEquals("skyblock-01", resolved.gamemodeKey());
        assertTrue(resolved.tracked());
        assertTrue(resolved.countedTowardsNetworkTotal());
    }

    @Test
    void resolveReturnsUntrackedWhenUnknownFallbackDisabled() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .resolveUnknownServersAsGamemode(false)
                .build();
        PlaytimeGamemodeResolver resolver = new PlaytimeGamemodeResolver(settings);

        PlaytimeGamemodeResolver.ResolvedGamemode resolved = resolver.resolve("minigames-01");

        assertEquals("minigames-01", resolved.serverName());
        assertNull(resolved.gamemodeKey());
        assertFalse(resolved.tracked());
        assertFalse(resolved.countedTowardsNetworkTotal());
    }
}
