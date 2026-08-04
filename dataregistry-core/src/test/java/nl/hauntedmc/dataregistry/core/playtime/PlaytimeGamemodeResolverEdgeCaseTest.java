package nl.hauntedmc.dataregistry.core.playtime;

import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeGamemodeResolverEdgeCaseTest {

    @Test
    void constructorRejectsMissingSettings() {
        assertThrows(NullPointerException.class, () -> new PlaytimeGamemodeResolver(null));
    }

    @Test
    void nullAndBlankServersResolveToUntrackedEmptyState() {
        PlaytimeGamemodeResolver resolver = new PlaytimeGamemodeResolver(PlaytimeTrackingSettings.defaults());

        for (String value : new String[]{null, "", "   "}) {
            PlaytimeGamemodeResolver.ResolvedGamemode resolved = resolver.resolve(value);
            assertNull(resolved.serverName());
            assertNull(resolved.gamemodeKey());
            assertFalse(resolved.tracked());
            assertFalse(resolved.countedTowardsNetworkTotal());
        }
    }

    @Test
    void firstMatchingRuleWinsEvenWhenLaterRuleIsMoreSpecific() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("survival-*", "survival"),
                        new PlaytimeTrackingSettings.ServerGamemodeRule("survival-events", "events")
                ))
                .build();

        PlaytimeGamemodeResolver.ResolvedGamemode resolved = new PlaytimeGamemodeResolver(settings)
                .resolve("survival-events");

        assertEquals("survival", resolved.gamemodeKey());
    }

    @Test
    void starWildcardBacktracksAcrossMultipleSegments() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .resolveUnknownServersAsGamemode(false)
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("event-*-final-*", "event")
                ))
                .build();

        PlaytimeGamemodeResolver resolver = new PlaytimeGamemodeResolver(settings);

        assertEquals("event", resolver.resolve("event-summer-round-final-2").gamemodeKey());
        assertNull(resolver.resolve("event-summer-round-2").gamemodeKey());
    }

    @Test
    void questionMarkMatchesExactlyOneCharacter() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .resolveUnknownServersAsGamemode(false)
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("proxy-?", "proxy")
                ))
                .build();
        PlaytimeGamemodeResolver resolver = new PlaytimeGamemodeResolver(settings);

        assertEquals("proxy", resolver.resolve("proxy-a").gamemodeKey());
        assertNull(resolver.resolve("proxy-").gamemodeKey());
        assertNull(resolver.resolve("proxy-ab").gamemodeKey());
    }

    @Test
    void trailingStarMatchesEmptyAndNonEmptySuffix() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .resolveUnknownServersAsGamemode(false)
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("hub*", "hub")
                ))
                .build();
        PlaytimeGamemodeResolver resolver = new PlaytimeGamemodeResolver(settings);

        assertEquals("hub", resolver.resolve("hub").gamemodeKey());
        assertEquals("hub", resolver.resolve("hub-eu-1").gamemodeKey());
    }

    @Test
    void ignoredGamemodeAlwaysDisablesNetworkCounting() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .ignoredGamemodes(Set.of("queue"))
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("queue-*", "queue")
                ))
                .build();

        PlaytimeGamemodeResolver.ResolvedGamemode resolved = new PlaytimeGamemodeResolver(settings)
                .resolve("queue-1");

        assertFalse(resolved.tracked());
        assertFalse(resolved.countedTowardsNetworkTotal());
    }

    @Test
    void trackedExcludedGamemodeRemainsTrackedButDoesNotCountTowardsNetworkTotal() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .excludedFromNetworkTotalGamemodes(Set.of("creative"))
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("creative-*", "creative")
                ))
                .build();

        PlaytimeGamemodeResolver.ResolvedGamemode resolved = new PlaytimeGamemodeResolver(settings)
                .resolve("creative-1");

        assertTrue(resolved.tracked());
        assertFalse(resolved.countedTowardsNetworkTotal());
    }

    @Test
    void queryBlacklistedGamemodeStillAccruesInternallyButNeverCountsTowardsNetworkTotal() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .queryBlacklistedGamemodes(Set.of("staff"))
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("staff-*", "staff")
                ))
                .build();

        PlaytimeGamemodeResolver.ResolvedGamemode resolved = new PlaytimeGamemodeResolver(settings)
                .resolve("staff-1");

        assertTrue(resolved.tracked());
        assertFalse(resolved.countedTowardsNetworkTotal());
        assertFalse(settings.catalog().isQueryable("staff"));
    }

    @Test
    void serverNameNormalizationUsesLocaleIndependentLowercase() {
        assertEquals("lobby-i", PlaytimeGamemodeResolver.normalizeServerNameOrNull(" LOBBY-I "));
        assertNull(PlaytimeGamemodeResolver.normalizeServerNameOrNull(null));
        assertNull(PlaytimeGamemodeResolver.normalizeServerNameOrNull(" "));
    }
}
