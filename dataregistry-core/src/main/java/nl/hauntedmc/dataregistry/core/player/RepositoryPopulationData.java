package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.population.PlayerPopulationMembership;
import nl.hauntedmc.dataregistry.api.population.PopulationData;
import nl.hauntedmc.dataregistry.api.population.PopulationJoinContext;
import nl.hauntedmc.dataregistry.api.population.PopulationResolvedGamemode;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.api.population.PopulationSnapshot;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionBatch;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionQuery;
import nl.hauntedmc.dataregistry.core.persistence.repository.PopulationRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Query-executor backed implementation of the public population facade. */
public final class RepositoryPopulationData implements PopulationData {

    static final String FIND_SNAPSHOT_OPERATION = "population.snapshot.find";
    static final String FIND_GAMEMODE_SNAPSHOTS_OPERATION = "population.gamemode-snapshots.find";
    static final String FIND_MEMBERSHIP_OPERATION = "population.membership.find";
    static final String FIND_MEMBERSHIPS_OPERATION = "population.memberships.find";
    static final String FIND_JOIN_CONTEXT_OPERATION = "population.join-context.find";
    static final String FIND_TRANSITIONS_OPERATION = "population.transitions.find";
    static final String LATEST_TRANSITION_ID_OPERATION = "population.transition.latest-id";

    private final PlayerDirectory playerDirectory;
    private final PopulationRepository repository;
    private final DataRegistryQueryExecutor queryExecutor;
    private final Function<String, PopulationResolvedGamemode> gamemodeResolver;

    public RepositoryPopulationData(
            PlayerDirectory playerDirectory,
            PopulationRepository repository,
            DataRegistryQueryExecutor queryExecutor,
            Function<String, PopulationResolvedGamemode> gamemodeResolver
    ) {
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor must not be null");
        this.gamemodeResolver = Objects.requireNonNull(gamemodeResolver, "gamemodeResolver must not be null");
    }

    @Override
    public CompletionStage<Optional<PopulationSnapshot>> findSnapshot(PopulationScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return queryExecutor.supply(FIND_SNAPSHOT_OPERATION, () -> repository.findSnapshot(scope));
    }

    @Override
    public CompletionStage<List<PopulationSnapshot>> findGamemodeSnapshots() {
        return queryExecutor.supply(FIND_GAMEMODE_SNAPSHOTS_OPERATION, repository::findGamemodeSnapshots);
    }

    @Override
    public CompletionStage<Optional<PlayerPopulationMembership>> findMembership(
            PlayerLookup player,
            PopulationScope scope
    ) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        return playerDirectory.findIdentity(player).thenCompose(identity -> identity
                .<CompletionStage<Optional<PlayerPopulationMembership>>>map(value -> queryExecutor.supply(
                        FIND_MEMBERSHIP_OPERATION,
                        () -> repository.findMembership(value.playerId(), scope)
                ))
                .orElseGet(() -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty())));
    }

    @Override
    public CompletionStage<List<PlayerPopulationMembership>> findMemberships(PlayerLookup player) {
        Objects.requireNonNull(player, "player must not be null");
        return playerDirectory.findIdentity(player).thenCompose(identity -> identity
                .<CompletionStage<List<PlayerPopulationMembership>>>map(value -> queryExecutor.supply(
                        FIND_MEMBERSHIPS_OPERATION,
                        () -> repository.findMemberships(value.playerId())
                ))
                .orElseGet(() -> java.util.concurrent.CompletableFuture.completedFuture(List.of())));
    }

    @Override
    public CompletionStage<Optional<PopulationJoinContext>> findJoinContext(UUID playerUuid, String serverName) {
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        PopulationResolvedGamemode resolved = gamemodeResolver.apply(serverName);
        return queryExecutor.supply(
                FIND_JOIN_CONTEXT_OPERATION,
                () -> repository.findJoinContext(playerUuid, resolved.serverName(), resolved)
        );
    }

    @Override
    public CompletionStage<PopulationResolvedGamemode> resolveGamemode(String serverName) {
        return java.util.concurrent.CompletableFuture.completedFuture(gamemodeResolver.apply(serverName));
    }

    @Override
    public CompletionStage<PopulationTransitionBatch> findTransitions(PopulationTransitionQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return queryExecutor.supply(FIND_TRANSITIONS_OPERATION, () -> repository.findTransitions(query));
    }

    @Override
    public CompletionStage<Long> latestTransitionId() {
        return queryExecutor.supply(LATEST_TRANSITION_ID_OPERATION, repository::latestTransitionId);
    }
}
