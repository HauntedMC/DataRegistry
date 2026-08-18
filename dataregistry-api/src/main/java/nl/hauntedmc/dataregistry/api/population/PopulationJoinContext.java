package nl.hauntedmc.dataregistry.api.population;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable correlation between the player's current session/visit and the memberships that created their ordinals.
 * This lets backend features identify a true first network/gamemode join without timestamp heuristics.
 */
public record PopulationJoinContext(
        PlayerIdentity player,
        String serverName,
        Optional<String> gamemodeKey,
        boolean networkFirstJoinThisSession,
        boolean gamemodeFirstJoinThisVisit,
        PlayerPopulationMembership networkMembership,
        Optional<PlayerPopulationMembership> gamemodeMembership,
        PopulationSnapshot networkSnapshot,
        Optional<PopulationSnapshot> gamemodeSnapshot,
        Instant generatedAt
) {
    public PopulationJoinContext {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(serverName, "serverName must not be null");
        gamemodeKey = Objects.requireNonNull(gamemodeKey, "gamemodeKey must not be null");
        Objects.requireNonNull(networkMembership, "networkMembership must not be null");
        gamemodeMembership = Objects.requireNonNull(gamemodeMembership, "gamemodeMembership must not be null");
        Objects.requireNonNull(networkSnapshot, "networkSnapshot must not be null");
        gamemodeSnapshot = Objects.requireNonNull(gamemodeSnapshot, "gamemodeSnapshot must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
