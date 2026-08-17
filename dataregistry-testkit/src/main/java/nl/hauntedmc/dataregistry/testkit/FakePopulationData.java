package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.population.PlayerPopulationMembership;
import nl.hauntedmc.dataregistry.api.population.PopulationData;
import nl.hauntedmc.dataregistry.api.population.PopulationJoinContext;
import nl.hauntedmc.dataregistry.api.population.PopulationResolvedGamemode;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.api.population.PopulationScopeType;
import nl.hauntedmc.dataregistry.api.population.PopulationSnapshot;
import nl.hauntedmc.dataregistry.api.population.PopulationTransition;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionBatch;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionQuery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Mutable in-memory population facade for downstream feature contract tests. */
public final class FakePopulationData implements PopulationData {

    private final Map<PopulationScope, PopulationSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<MembershipKey, PlayerPopulationMembership> memberships = new ConcurrentHashMap<>();
    private final Map<JoinKey, PopulationJoinContext> joinContexts = new ConcurrentHashMap<>();
    private final List<PopulationTransition> transitions = new CopyOnWriteArrayList<>();
    private volatile Function<String, PopulationResolvedGamemode> gamemodeResolver = serverName ->
            new PopulationResolvedGamemode(serverName, null, false, false);

    public FakePopulationData putSnapshot(PopulationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        snapshots.put(snapshot.scope(), snapshot);
        return this;
    }

    public FakePopulationData putMembership(PlayerLookup lookup, PlayerPopulationMembership membership) {
        Objects.requireNonNull(lookup, "lookup must not be null");
        Objects.requireNonNull(membership, "membership must not be null");
        memberships.put(new MembershipKey(lookup, membership.scope()), membership);
        return this;
    }

    public FakePopulationData putJoinContext(UUID uuid, String serverName, PopulationJoinContext context) {
        joinContexts.put(new JoinKey(uuid, normalizeServer(serverName)), Objects.requireNonNull(context));
        return this;
    }

    public FakePopulationData setGamemodeResolver(Function<String, PopulationResolvedGamemode> resolver) {
        gamemodeResolver = Objects.requireNonNull(resolver, "resolver must not be null");
        return this;
    }

    public FakePopulationData addTransition(PopulationTransition transition) {
        transitions.add(Objects.requireNonNull(transition, "transition must not be null"));
        transitions.sort(Comparator.comparingLong(PopulationTransition::id));
        return this;
    }

    @Override
    public CompletionStage<Optional<PopulationSnapshot>> findSnapshot(PopulationScope scope) {
        return CompletableFuture.completedFuture(Optional.ofNullable(snapshots.get(scope)));
    }

    @Override
    public CompletionStage<List<PopulationSnapshot>> findGamemodeSnapshots() {
        return CompletableFuture.completedFuture(snapshots.values().stream()
                .filter(snapshot -> snapshot.scope().type() == PopulationScopeType.GAMEMODE)
                .sorted(Comparator.comparing(snapshot -> snapshot.scope().key()))
                .toList());
    }

    @Override
    public CompletionStage<Optional<PlayerPopulationMembership>> findMembership(
            PlayerLookup player,
            PopulationScope scope
    ) {
        return CompletableFuture.completedFuture(Optional.ofNullable(memberships.get(new MembershipKey(player, scope))));
    }

    @Override
    public CompletionStage<List<PlayerPopulationMembership>> findMemberships(PlayerLookup player) {
        return CompletableFuture.completedFuture(memberships.entrySet().stream()
                .filter(entry -> entry.getKey().player().equals(player))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(value -> value.scope().storageKey()))
                .toList());
    }

    @Override
    public CompletionStage<Optional<PopulationJoinContext>> findJoinContext(UUID playerUuid, String serverName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(
                joinContexts.get(new JoinKey(playerUuid, normalizeServer(serverName)))
        ));
    }

    @Override
    public CompletionStage<PopulationResolvedGamemode> resolveGamemode(String serverName) {
        return CompletableFuture.completedFuture(gamemodeResolver.apply(serverName));
    }

    @Override
    public CompletionStage<PopulationTransitionBatch> findTransitions(PopulationTransitionQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        List<PopulationTransition> retained = new ArrayList<>(transitions);
        long earliest = retained.isEmpty() ? 0L : retained.getFirst().id();
        long latest = retained.isEmpty() ? 0L : retained.getLast().id();
        List<PopulationTransition> selected = retained.stream()
                .filter(transition -> transition.id() > query.afterId())
                .filter(transition -> query.scope() == null || query.scope().equals(transition.scope()))
                .filter(transition -> query.types().isEmpty() || query.types().contains(transition.type()))
                .filter(transition -> query.causes().isEmpty() || query.causes().contains(transition.cause()))
                .limit(query.limit())
                .toList();
        return CompletableFuture.completedFuture(new PopulationTransitionBatch(
                earliest,
                latest,
                selected,
                Instant.now()
        ));
    }

    private static String normalizeServer(String serverName) {
        return serverName == null ? "" : serverName.trim().toLowerCase(Locale.ROOT);
    }

    private record MembershipKey(PlayerLookup player, PopulationScope scope) {
        private MembershipKey {
            Objects.requireNonNull(player, "player must not be null");
            Objects.requireNonNull(scope, "scope must not be null");
        }
    }

    private record JoinKey(UUID uuid, String serverName) {
        private JoinKey {
            Objects.requireNonNull(uuid, "uuid must not be null");
            Objects.requireNonNull(serverName, "serverName must not be null");
        }
    }
}
