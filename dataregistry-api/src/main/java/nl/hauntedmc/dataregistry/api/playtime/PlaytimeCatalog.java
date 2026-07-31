package nl.hauntedmc.dataregistry.api.playtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, configuration-backed catalog of public playtime gamemode policy.
 * <p>
 * Gamemodes not explicitly listed inherit the default tracked/queryable/network-total policy only when
 * unknown backend servers are configured to resolve to their normalized server name.
 */
public record PlaytimeCatalog(
        boolean resolvesUnknownGamemodes,
        List<PlaytimeGamemodeDefinition> gamemodes
) {

    public PlaytimeCatalog {
        List<PlaytimeGamemodeDefinition> source = gamemodes == null ? List.of() : List.copyOf(gamemodes);
        Map<String, PlaytimeGamemodeDefinition> unique = new LinkedHashMap<>();
        for (PlaytimeGamemodeDefinition definition : source) {
            if (definition == null) {
                throw new IllegalArgumentException("gamemodes must not contain null definitions.");
            }
            PlaytimeGamemodeDefinition previous = unique.putIfAbsent(definition.gamemodeKey(), definition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate playtime gamemode definition for key '" + definition.gamemodeKey() + "'."
                );
            }
        }
        gamemodes = List.copyOf(unique.values());
    }

    public static PlaytimeCatalog empty() {
        return new PlaytimeCatalog(false, List.of());
    }

    public Optional<PlaytimeGamemodeDefinition> find(String gamemodeKey) {
        String normalized = PlaytimeGamemodeDefinition.normalizeGamemodeKeyOrNull(gamemodeKey);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<PlaytimeGamemodeDefinition> configured = gamemodes.stream()
                .filter(definition -> definition.gamemodeKey().equals(normalized))
                .findFirst();
        if (configured.isPresent() || !resolvesUnknownGamemodes) {
            return configured;
        }
        return Optional.of(new PlaytimeGamemodeDefinition(normalized, true, true, true));
    }

    public boolean isQueryable(String gamemodeKey) {
        return find(gamemodeKey).map(PlaytimeGamemodeDefinition::queryable).orElse(false);
    }

    public boolean isCountedTowardsNetworkTotal(String gamemodeKey) {
        return find(gamemodeKey)
                .map(PlaytimeGamemodeDefinition::countedTowardsNetworkTotal)
                .orElse(false);
    }
}
