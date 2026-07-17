package nl.hauntedmc.dataregistry.backend.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable runtime settings for the playtime domain.
 */
public final class PlaytimeTrackingSettings {

    private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 30;
    private static final boolean DEFAULT_RESOLVE_UNKNOWN_SERVERS_AS_GAMEMODE = true;
    private static final int DEFAULT_GAMEMODE_KEY_MAX_LENGTH = 64;
    private static final int MAX_SERVER_PATTERN_LENGTH = 128;
    private static final String ALLOWED_GAMEMODE_KEY_REGEX = "[a-z0-9._:-]+";

    private final int flushIntervalSeconds;
    private final boolean resolveUnknownServersAsGamemode;
    private final int gamemodeKeyMaxLength;
    private final Set<String> ignoredGamemodes;
    private final Set<String> excludedFromNetworkTotalGamemodes;
    private final List<ServerGamemodeRule> serverGamemodeRules;

    private PlaytimeTrackingSettings(Builder builder) {
        this.flushIntervalSeconds = validateRange(
                builder.flushIntervalSeconds,
                "flushIntervalSeconds",
                5,
                300
        );
        this.resolveUnknownServersAsGamemode = builder.resolveUnknownServersAsGamemode;
        this.gamemodeKeyMaxLength = validateRange(
                builder.gamemodeKeyMaxLength,
                "gamemodeKeyMaxLength",
                1,
                64
        );
        this.ignoredGamemodes = Collections.unmodifiableSet(
                normalizeGamemodeKeys(builder.ignoredGamemodes, gamemodeKeyMaxLength, "ignoredGamemodes")
        );
        this.excludedFromNetworkTotalGamemodes = Collections.unmodifiableSet(
                normalizeGamemodeKeys(
                        builder.excludedFromNetworkTotalGamemodes,
                        gamemodeKeyMaxLength,
                        "excludedFromNetworkTotalGamemodes"
                )
        );
        this.serverGamemodeRules = Collections.unmodifiableList(
                normalizeRules(builder.serverGamemodeRules, gamemodeKeyMaxLength)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PlaytimeTrackingSettings defaults() {
        return builder().build();
    }

    public int flushIntervalSeconds() {
        return flushIntervalSeconds;
    }

    public boolean resolveUnknownServersAsGamemode() {
        return resolveUnknownServersAsGamemode;
    }

    public int gamemodeKeyMaxLength() {
        return gamemodeKeyMaxLength;
    }

    public Set<String> ignoredGamemodes() {
        return ignoredGamemodes;
    }

    public boolean isIgnoredGamemode(String gamemodeKey) {
        String normalized = normalizeGamemodeKeyOrNull(gamemodeKey, gamemodeKeyMaxLength);
        return normalized != null && ignoredGamemodes.contains(normalized);
    }

    public Set<String> excludedFromNetworkTotalGamemodes() {
        return excludedFromNetworkTotalGamemodes;
    }

    public boolean isExcludedFromNetworkTotal(String gamemodeKey) {
        String normalized = normalizeGamemodeKeyOrNull(gamemodeKey, gamemodeKeyMaxLength);
        return normalized != null && excludedFromNetworkTotalGamemodes.contains(normalized);
    }

    public List<ServerGamemodeRule> serverGamemodeRules() {
        return serverGamemodeRules;
    }

    public static String normalizeGamemodeKeyOrNull(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            return null;
        }
        if (!normalized.matches(ALLOWED_GAMEMODE_KEY_REGEX)) {
            return null;
        }
        return normalized;
    }

    static String normalizeServerPattern(String value) {
        if (value == null) {
            throw new IllegalArgumentException("match must not be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("match must not be blank");
        }
        if (normalized.length() > MAX_SERVER_PATTERN_LENGTH) {
            throw new IllegalArgumentException(
                    "match must be at most " + MAX_SERVER_PATTERN_LENGTH + " characters."
            );
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            boolean allowed = character == '*'
                    || character == '?'
                    || character == '-'
                    || character == '_'
                    || character == '.'
                    || character == ':'
                    || Character.isLetterOrDigit(character);
            if (!allowed) {
                throw new IllegalArgumentException(
                        "match contains unsupported character '" + character + "'."
                );
            }
        }
        return normalized;
    }

    private static int validateRange(int value, String fieldName, int minInclusive, int maxInclusive) {
        if (value < minInclusive || value > maxInclusive) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + minInclusive + " and " + maxInclusive + "."
            );
        }
        return value;
    }

    private static Set<String> normalizeGamemodeKeys(
            Set<String> values,
            int maxLength,
            String fieldName
    ) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedValue = normalizeGamemodeKeyOrNull(value, maxLength);
            if (normalizedValue == null) {
                throw new IllegalArgumentException(
                        fieldName + " contains an invalid gamemode key: '" + value + "'."
                );
            }
            normalized.add(normalizedValue);
        }
        return normalized;
    }

    private static List<ServerGamemodeRule> normalizeRules(
            List<ServerGamemodeRule> values,
            int maxLength
    ) {
        Objects.requireNonNull(values, "serverGamemodeRules must not be null");
        List<ServerGamemodeRule> normalized = new ArrayList<>(values.size());
        for (ServerGamemodeRule value : values) {
            Objects.requireNonNull(value, "serverGamemodeRules must not contain null values");
            normalized.add(new ServerGamemodeRule(
                    normalizeServerPattern(value.match()),
                    normalizeRequiredGamemodeKey(value.gamemodeKey(), maxLength)
            ));
        }
        return normalized;
    }

    private static String normalizeRequiredGamemodeKey(String value, int maxLength) {
        String normalized = normalizeGamemodeKeyOrNull(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException("gamemodeKey must be a non-blank normalized key.");
        }
        return normalized;
    }

    public record ServerGamemodeRule(String match, String gamemodeKey) {

        public ServerGamemodeRule {
            match = normalizeServerPattern(match);
            gamemodeKey = normalizeRequiredGamemodeKey(
                    gamemodeKey,
                    DEFAULT_GAMEMODE_KEY_MAX_LENGTH
            );
        }
    }

    public static final class Builder {
        private int flushIntervalSeconds = DEFAULT_FLUSH_INTERVAL_SECONDS;
        private boolean resolveUnknownServersAsGamemode = DEFAULT_RESOLVE_UNKNOWN_SERVERS_AS_GAMEMODE;
        private int gamemodeKeyMaxLength = DEFAULT_GAMEMODE_KEY_MAX_LENGTH;
        private Set<String> ignoredGamemodes = Set.of();
        private Set<String> excludedFromNetworkTotalGamemodes = Set.of();
        private List<ServerGamemodeRule> serverGamemodeRules = List.of();

        private Builder() {
        }

        public Builder flushIntervalSeconds(int value) {
            this.flushIntervalSeconds = value;
            return this;
        }

        public Builder resolveUnknownServersAsGamemode(boolean value) {
            this.resolveUnknownServersAsGamemode = value;
            return this;
        }

        public Builder gamemodeKeyMaxLength(int value) {
            this.gamemodeKeyMaxLength = value;
            return this;
        }

        public Builder ignoredGamemodes(Set<String> values) {
            this.ignoredGamemodes = values == null ? Set.of() : Set.copyOf(values);
            return this;
        }

        public Builder excludedFromNetworkTotalGamemodes(Set<String> values) {
            this.excludedFromNetworkTotalGamemodes = values == null ? Set.of() : Set.copyOf(values);
            return this;
        }

        public Builder serverGamemodeRules(List<ServerGamemodeRule> values) {
            this.serverGamemodeRules = values == null ? List.of() : List.copyOf(values);
            return this;
        }

        public PlaytimeTrackingSettings build() {
            return new PlaytimeTrackingSettings(this);
        }
    }
}
