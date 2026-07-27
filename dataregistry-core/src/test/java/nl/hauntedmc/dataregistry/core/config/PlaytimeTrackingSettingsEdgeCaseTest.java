package nl.hauntedmc.dataregistry.core.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeTrackingSettingsEdgeCaseTest {

    @Test
    void rangeBoundariesAreAccepted() {
        PlaytimeTrackingSettings minimum = PlaytimeTrackingSettings.builder()
                .flushIntervalSeconds(5)
                .gamemodeKeyMaxLength(1)
                .build();
        PlaytimeTrackingSettings maximum = PlaytimeTrackingSettings.builder()
                .flushIntervalSeconds(300)
                .gamemodeKeyMaxLength(64)
                .build();

        assertEquals(5, minimum.flushIntervalSeconds());
        assertEquals(1, minimum.gamemodeKeyMaxLength());
        assertEquals(300, maximum.flushIntervalSeconds());
        assertEquals(64, maximum.gamemodeKeyMaxLength());
    }

    @Test
    void allDocumentedGamemodeKeyCharactersAreAcceptedAndNormalized() {
        assertEquals(
                "survival.one_two:three-four",
                PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull(" Survival.One_Two:Three-Four ", 64)
        );
    }

    @Test
    void invalidOrOverlongGamemodeKeysNormalizeToNull() {
        assertEquals(null, PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull(null, 64));
        assertEquals(null, PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull(" ", 64));
        assertEquals(null, PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull("lobby eu", 64));
        assertEquals(null, PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull("lobby*", 64));
        assertEquals(null, PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull("abcdef", 5));
    }

    @Test
    void duplicateKeysCollapseAfterNormalizationWhileRetainingInsertionOrder() {
        LinkedHashSet<String> ignored = new LinkedHashSet<>();
        ignored.add(" Lobby ");
        ignored.add("lobby");
        ignored.add("Queue");

        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .ignoredGamemodes(ignored)
                .build();

        assertEquals(List.of("lobby", "queue"), List.copyOf(settings.ignoredGamemodes()));
    }

    @Test
    void builtCollectionsAreImmutableAndDetachedFromBuilderInputs() {
        Set<String> ignored = new LinkedHashSet<>(Set.of("lobby"));
        List<PlaytimeTrackingSettings.ServerGamemodeRule> rules = new ArrayList<>(List.of(
                new PlaytimeTrackingSettings.ServerGamemodeRule("lobby-*", "lobby")
        ));
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .ignoredGamemodes(ignored)
                .serverGamemodeRules(rules)
                .build();
        ignored.add("queue");
        rules.clear();

        assertEquals(Set.of("lobby"), settings.ignoredGamemodes());
        assertEquals(1, settings.serverGamemodeRules().size());
        assertThrows(UnsupportedOperationException.class, () -> settings.ignoredGamemodes().add("queue"));
        assertThrows(UnsupportedOperationException.class, () -> settings.serverGamemodeRules().clear());
    }

    @Test
    void nullBuilderCollectionsProduceEmptyCollections() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .ignoredGamemodes(null)
                .excludedFromNetworkTotalGamemodes(null)
                .serverGamemodeRules(null)
                .build();

        assertTrue(settings.ignoredGamemodes().isEmpty());
        assertTrue(settings.excludedFromNetworkTotalGamemodes().isEmpty());
        assertTrue(settings.serverGamemodeRules().isEmpty());
    }

    @Test
    void serverPatternAcceptsExactLengthBoundaryAndRejectsUnsupportedCharacters() {
        String boundary = "a".repeat(128);

        PlaytimeTrackingSettings.ServerGamemodeRule rule =
                new PlaytimeTrackingSettings.ServerGamemodeRule(boundary, "mode");

        assertEquals(boundary, rule.match());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaytimeTrackingSettings.ServerGamemodeRule("a".repeat(129), "mode")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaytimeTrackingSettings.ServerGamemodeRule("lobby/[1]", "mode")
        );
    }

    @Test
    void rulesRejectNullAndInvalidGamemodeKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaytimeTrackingSettings.ServerGamemodeRule(null, "mode")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaytimeTrackingSettings.ServerGamemodeRule("lobby-*", " ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaytimeTrackingSettings.ServerGamemodeRule("lobby-*", "bad key")
        );
    }

    @Test
    void membershipChecksHandleNullBlankInvalidAndCaseVariants() {
        PlaytimeTrackingSettings settings = PlaytimeTrackingSettings.builder()
                .ignoredGamemodes(Set.of("queue"))
                .excludedFromNetworkTotalGamemodes(Set.of("lobby"))
                .build();

        assertTrue(settings.isIgnoredGamemode(" QUEUE "));
        assertTrue(settings.isExcludedFromNetworkTotal("Lobby"));
        assertFalse(settings.isIgnoredGamemode(null));
        assertFalse(settings.isIgnoredGamemode(" "));
        assertFalse(settings.isExcludedFromNetworkTotal("bad key"));
    }
}
