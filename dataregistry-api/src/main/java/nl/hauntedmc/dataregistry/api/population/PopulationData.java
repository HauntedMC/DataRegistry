package nl.hauntedmc.dataregistry.api.population;

import nl.hauntedmc.dataregistry.api.player.PlayerLookup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Public asynchronous read facade for canonical network and logical-gamemode population state. */
public interface PopulationData {

    CompletionStage<Optional<PopulationSnapshot>> findSnapshot(PopulationScope scope);

    default CompletionStage<Optional<PopulationSnapshot>> findNetworkSnapshot() {
        return findSnapshot(PopulationScope.network());
    }

    CompletionStage<List<PopulationSnapshot>> findGamemodeSnapshots();

    CompletionStage<Optional<PlayerPopulationMembership>> findMembership(
            PlayerLookup player,
            PopulationScope scope
    );

    CompletionStage<List<PlayerPopulationMembership>> findMemberships(PlayerLookup player);

    CompletionStage<Optional<PopulationJoinContext>> findJoinContext(UUID playerUuid, String serverName);

    CompletionStage<PopulationResolvedGamemode> resolveGamemode(String serverName);

    CompletionStage<PopulationTransitionBatch> findTransitions(PopulationTransitionQuery query);
}
