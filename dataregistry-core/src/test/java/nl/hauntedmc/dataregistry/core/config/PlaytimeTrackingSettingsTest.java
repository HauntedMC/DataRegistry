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
        assertTrue(settings.ignoredGamemodes().isEmpty());
        assertTrue(settings.excludedFromNetworkTotalGamemodes().isEmpty());
        assertTrue(settings.serverGamemodeRules().isEmpty());
    }

    @Test
    void builderNormalizesGamemodeKeysAndRules() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .flushIntervalSeconds(45)
                .resolveUnknownServersAsGamemode(false)
                .ignoredGamemodes(Set.of(" Queue ", "Lobby"))
                .excludedFromNetworkTotalGamemodes(Set.of(" Lobby "))
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule(" LOBBY-* ", " Lobby "),
                        new PlaytimeTrackingSettings.ServerGamemodeRule("skyblock-?", "SkyBlock")
                ))
                .build();

        assertEquals(45, settings.flushIntervalSeconds());
        assertFalse(settings.resolveUnknownServersAsGamemode());
        assertTrue(settings.ignoredGamemodes().contains("queue"));
        assertTrue(settings.isIgnoredGamemode("QUEUE"));
        assertTrue(settings.excludedFromNetworkTotalGamemodes().contains("lobby"));
        assertTrue(settings.isExcludedFromNetworkTotal("Lobby"));
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
                () -> PlaytimeTrackingSettings.builder()
                        .serverGamemodeRules(List.of(
                                new PlaytimeTrackingSettings.ServerGamemodeRule("bad pattern!", "lobby")
                        ))
                        .build()
        );
    }
}
