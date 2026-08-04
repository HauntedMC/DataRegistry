package nl.hauntedmc.dataregistry.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeTrackingSettingsTest {

    @Test
    void defaultsProvideExpectedSafeValues() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.defaults();

        assertEquals(30, settings.flushIntervalSeconds());
        assertTrue(settings.resolveUnknownServersAsGamemode());
        assertEquals(64, settings.gamemodeKeyMaxLength());
        assertTrue(settings.blacklistedServerPatterns().isEmpty());
        assertTrue(settings.ignoredGamemodes().isEmpty());
        assertTrue(settings.queryBlacklistedGamemodes().isEmpty());
        assertTrue(settings.publicQueryExcludedGamemodes().isEmpty());
        assertTrue(settings.excludedFromNetworkTotalGamemodes().isEmpty());
        assertTrue(settings.networkTotalExcludedGamemodes().isEmpty());
        assertTrue(settings.serverGamemodeRules().isEmpty());
        assertTrue(settings.catalog().resolvesUnknownGamemodes());
    }

    @Test
    void builderNormalizesGamemodeKeysAndRules() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .flushIntervalSeconds(45)
                .resolveUnknownServersAsGamemode(false)
                .blacklistedServerPatterns(List.of(" DEV-* ", "dev-*", "limbo-?"))
                .ignoredGamemodes(Set.of(" Queue ", "Lobby"))
                .queryBlacklistedGamemodes(Set.of(" Staff "))
                .excludedFromNetworkTotalGamemodes(Set.of(" Lobby "))
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule(" LOBBY-* ", " Lobby "),
                        new PlaytimeTrackingSettings.ServerGamemodeRule("skyblock-?", "SkyBlock")
                ))
                .build();

        assertEquals(45, settings.flushIntervalSeconds());
        assertFalse(settings.resolveUnknownServersAsGamemode());
        assertEquals(List.of("dev-*", "limbo-?"), settings.blacklistedServerPatterns());
        assertTrue(settings.ignoredGamemodes().contains("queue"));
        assertTrue(settings.isIgnoredGamemode("QUEUE"));
        assertTrue(settings.queryBlacklistedGamemodes().contains("staff"));
        assertTrue(settings.excludedFromNetworkTotalGamemodes().contains("lobby"));
        assertEquals(Set.of("queue", "lobby", "staff"), settings.networkTotalExcludedGamemodes());
        assertEquals(Set.of("queue", "lobby", "staff"), settings.publicQueryExcludedGamemodes());
        assertTrue(settings.isExcludedFromNetworkTotal("Lobby"));
        assertTrue(settings.isExcludedFromNetworkTotal("Staff"));
        assertFalse(settings.catalog().find("staff").orElseThrow().queryable());
        assertFalse(settings.catalog().find("staff").orElseThrow().countedTowardsNetworkTotal());
        assertFalse(settings.catalog().find("queue").orElseThrow().tracked());
        assertTrue(settings.catalog().find("skyblock").orElseThrow().queryable());
        assertEquals("lobby-*", settings.serverGamemodeRules().get(0).match());
        assertEquals("lobby", settings.serverGamemodeRules().get(0).gamemodeKey());
        assertEquals("skyblock", settings.serverGamemodeRules().get(1).gamemodeKey());
    }

    @Test
    void builderRejectsInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaytimeTrackingSettings.builder().flushIntervalSeconds(4).build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaytimeTrackingSettings.builder().gamemodeKeyMaxLength(0).build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaytimeTrackingSettings.builder().ignoredGamemodes(Set.of("invalid value")).build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaytimeTrackingSettings.builder().queryBlacklistedGamemodes(Set.of("invalid value")).build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaytimeTrackingSettings.builder().blacklistedServerPatterns(List.of("bad pattern!")).build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaytimeTrackingSettings.builder()
                        .serverGamemodeRules(List.of(
                                new PlaytimeTrackingSettings.ServerGamemodeRule("bad pattern!", "lobby")
                        ))
                        .build()
        );
    }
}
