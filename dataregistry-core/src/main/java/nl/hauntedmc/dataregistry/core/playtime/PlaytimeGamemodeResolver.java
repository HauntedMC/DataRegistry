package nl.hauntedmc.dataregistry.core.playtime;

import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;

import java.util.Locale;
import java.util.Objects;

/**
 * Resolves backend server names to logical playtime gamemode keys.
 */
public final class PlaytimeGamemodeResolver {

    private final PlaytimeTrackingSettings settings;

    public PlaytimeGamemodeResolver(PlaytimeTrackingSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    public ResolvedGamemode resolve(String serverName) {
        String normalizedServerName = normalizeServerNameOrNull(serverName);
        if (normalizedServerName == null) {
            return new ResolvedGamemode(null, null, false, false);
        }

        String gamemodeKey = resolveGamemodeKey(normalizedServerName);
        if (gamemodeKey == null) {
            return new ResolvedGamemode(normalizedServerName, null, false, false);
        }

        boolean tracked = !settings.isIgnoredGamemode(gamemodeKey);
        boolean countedTowardsNetworkTotal = tracked && !settings.isExcludedFromNetworkTotal(gamemodeKey);
        return new ResolvedGamemode(
                normalizedServerName,
                gamemodeKey,
                tracked,
                countedTowardsNetworkTotal
        );
    }

    public static String normalizeServerNameOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveGamemodeKey(String normalizedServerName) {
        for (PlaytimeTrackingSettings.ServerGamemodeRule rule : settings.serverGamemodeRules()) {
            if (globMatches(rule.match(), normalizedServerName)) {
                return rule.gamemodeKey();
            }
        }
        if (!settings.resolveUnknownServersAsGamemode()) {
            return null;
        }
        return PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull(
                normalizedServerName,
                settings.gamemodeKeyMaxLength()
        );
    }

    private static boolean globMatches(String pattern, String value) {
        int patternIndex = 0;
        int valueIndex = 0;
        int starPatternIndex = -1;
        int starValueIndex = -1;

        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length()
                    && (pattern.charAt(patternIndex) == '?'
                    || pattern.charAt(patternIndex) == value.charAt(valueIndex))) {
                patternIndex++;
                valueIndex++;
                continue;
            }
            if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starPatternIndex = patternIndex++;
                starValueIndex = valueIndex;
                continue;
            }
            if (starPatternIndex != -1) {
                patternIndex = starPatternIndex + 1;
                valueIndex = ++starValueIndex;
                continue;
            }
            return false;
        }

        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    public record ResolvedGamemode(
            String serverName,
            String gamemodeKey,
            boolean tracked,
            boolean countedTowardsNetworkTotal
    ) {
    }
}
