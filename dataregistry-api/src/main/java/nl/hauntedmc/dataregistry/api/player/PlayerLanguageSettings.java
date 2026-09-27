package nl.hauntedmc.dataregistry.api.player;

import java.util.Objects;

/**
 * Immutable language preference stored by DataRegistry.
 *
 * @param playerId          stable DataRegistry player id.
 * @param language          stored preference code, for example {@code AUTO}, {@code EN}, or {@code NL}.
 * @param effectiveLanguage resolved effective language code used by downstream features.
 * @param version           optimistic revision of the language row.
 */
public record PlayerLanguageSettings(long playerId, String language, String effectiveLanguage, long version) {

    public PlayerLanguageSettings(long playerId, String language, String effectiveLanguage) {
        this(playerId, language, effectiveLanguage, 0L);
    }

    /**
     * Creates a validated language snapshot.
     */
    public PlayerLanguageSettings {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
        Objects.requireNonNull(language, "language must not be null");
        if (version < 0L) throw new IllegalArgumentException("version must not be negative.");
    }
}
