package nl.hauntedmc.dataregistry.api.player;

import java.util.Objects;

/**
 * Immutable language preference stored by DataRegistry.
 *
 * @param playerId          stable DataRegistry player id.
 * @param language          stored preference code, for example {@code AUTO}, {@code EN}, or {@code NL}.
 * @param effectiveLanguage resolved effective language code used by downstream features.
 */
public record PlayerLanguageSettings(long playerId, String language, String effectiveLanguage) {

    /**
     * Creates a validated language snapshot.
     */
    public PlayerLanguageSettings {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
        Objects.requireNonNull(language, "language must not be null");
    }
}
