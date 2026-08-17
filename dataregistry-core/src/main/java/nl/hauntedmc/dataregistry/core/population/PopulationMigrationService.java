package nl.hauntedmc.dataregistry.core.population;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationOrdinalQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPopulationMembershipEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationScopeStateEntity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One-time, idempotent bootstrap of population membership from existing canonical DataRegistry data. */
public final class PopulationMigrationService {

    public static final int BACKFILL_VERSION = 1;

    private final DataRegistry dataRegistry;

    public PopulationMigrationService(DataRegistry dataRegistry) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
    }

    public PopulationMigrationResult migrate() {
        if (!dataRegistry.isFeatureEnabled(DataRegistryFeature.POPULATION)) {
            return new PopulationMigrationResult(0L, 0L, PopulationBaselineQuality.TRACKED_ONLY, false);
        }
        return dataRegistry.getORM().runInTransaction(session -> {
            Instant now = Instant.now();
            long knownPlayers = session.createQuery("SELECT COUNT(p) FROM PlayerEntity p", Long.class).getSingleResult();
            PopulationBaselineQuality initialQuality = knownPlayers == 0L
                    ? PopulationBaselineQuality.VERIFIED
                    : PopulationBaselineQuality.TRACKED_ONLY;
            PopulationScopeStateEntity networkState = PopulationPersistence.ensureAndLockScopeState(
                    session,
                    PopulationScope.network(),
                    initialQuality,
                    initialQuality,
                    now
            );
            if (networkState.getBackfillVersion() >= BACKFILL_VERSION) {
                return new PopulationMigrationResult(
                        0L,
                        0L,
                        networkState.getMembershipBaselineQuality(),
                        false
                );
            }

            long networkAdded = backfillNetworkMemberships(session, networkState, now);
            long gamemodeAdded = dataRegistry.isFeatureEnabled(DataRegistryFeature.PLAYTIME)
                    ? backfillGamemodeMemberships(session, networkState.getMembershipBaselineQuality(), now)
                    : 0L;
            networkState.setBackfillVersion(BACKFILL_VERSION);
            networkState.setUpdatedAt(now);
            return new PopulationMigrationResult(
                    networkAdded,
                    gamemodeAdded,
                    networkState.getMembershipBaselineQuality(),
                    true
            );
        });
    }

    private static long backfillNetworkMemberships(
            org.hibernate.Session session,
            PopulationScopeStateEntity state,
            Instant now
    ) {
        List<PlayerEntity> players = session.createQuery(
                        "SELECT p FROM PlayerEntity p ORDER BY p.id ASC",
                        PlayerEntity.class
                )
                .list();
        long added = 0L;
        for (PlayerEntity player : players) {
            if (PopulationPersistence.findMembership(session, player.getId(), PopulationScope.network()) != null) {
                continue;
            }
            PlayerSessionEntity firstSession = session.createQuery(
                            "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :playerId " +
                                    "ORDER BY s.startedAt ASC, s.id ASC",
                            PlayerSessionEntity.class
                    )
                    .setParameter("playerId", player.getId())
                    .setMaxResults(1)
                    .uniqueResult();
            long ordinal = Math.addExact(state.getUniquePlayerCount(), 1L);
            persistBackfilledMembership(
                    session,
                    player,
                    PopulationScope.network(),
                    ordinal,
                    firstSession == null ? null : firstSession.getStartedAt(),
                    firstSession == null ? null : firstSession.getId(),
                    null,
                    now
            );
            state.setUniquePlayerCount(ordinal);
            added++;
        }
        return added;
    }

    private static long backfillGamemodeMemberships(
            org.hibernate.Session session,
            PopulationBaselineQuality baselineQuality,
            Instant now
    ) {
        List<PlayerPlaytimeEntity> aggregates = session.createQuery(
                        "SELECT a FROM PlayerPlaytimeEntity a JOIN FETCH a.player " +
                                "ORDER BY a.gamemodeKey ASC, a.firstTrackedAt ASC, a.player.id ASC",
                        PlayerPlaytimeEntity.class
                )
                .list();
        long added = 0L;
        String lastScopeId = null;
        PopulationScopeStateEntity state = null;
        for (PlayerPlaytimeEntity aggregate : aggregates) {
            PopulationScope scope = PopulationScope.gamemode(aggregate.getGamemodeKey());
            if (!scope.storageKey().equals(lastScopeId)) {
                state = PopulationPersistence.ensureAndLockScopeState(
                        session,
                        scope,
                        baselineQuality,
                        baselineQuality,
                        now
                );
                state.setBackfillVersion(BACKFILL_VERSION);
                lastScopeId = scope.storageKey();
            }
            if (PopulationPersistence.findMembership(session, aggregate.getPlayer().getId(), scope) != null) {
                continue;
            }
            long ordinal = Math.addExact(state.getUniquePlayerCount(), 1L);
            persistBackfilledMembership(
                    session,
                    aggregate.getPlayer(),
                    scope,
                    ordinal,
                    aggregate.getFirstTrackedAt(),
                    null,
                    null,
                    now
            );
            state.setUniquePlayerCount(ordinal);
            state.setUpdatedAt(now);
            added++;
        }
        return added;
    }

    private static void persistBackfilledMembership(
            org.hibernate.Session session,
            PlayerEntity player,
            PopulationScope scope,
            long ordinal,
            Instant firstJoinedAt,
            Long firstSessionId,
            Long firstVisitId,
            Instant createdAt
    ) {
        PlayerPopulationMembershipEntity membership = new PlayerPopulationMembershipEntity();
        membership.setPlayer(player);
        membership.setScopeId(scope.storageKey());
        membership.setScopeType(scope.type());
        membership.setScopeKey(scope.key());
        membership.setOrdinal(ordinal);
        membership.setOrdinalQuality(PopulationOrdinalQuality.BACKFILLED_DETERMINISTIC);
        membership.setFirstJoinedAt(firstJoinedAt);
        membership.setFirstSessionId(firstSessionId);
        membership.setFirstVisitId(firstVisitId);
        membership.setCreatedAt(createdAt);
        session.persist(membership);
    }
}
