package nl.hauntedmc.dataregistry.api.player;

import java.util.Objects;
import java.util.Optional;

/**
 * Result envelope for asynchronous profile lookups.
 */
public record PlayerProfileResult(
        PlayerLookup lookup,
        PlayerProfileQuery query,
        Optional<PlayerProfile> profile
) {

    public PlayerProfileResult {
        lookup = Objects.requireNonNull(lookup, "lookup must not be null");
        query = Objects.requireNonNull(query, "query must not be null");
        profile = Objects.requireNonNull(profile, "profile must not be null");
    }

    public boolean found() {
        return profile.isPresent();
    }
}
