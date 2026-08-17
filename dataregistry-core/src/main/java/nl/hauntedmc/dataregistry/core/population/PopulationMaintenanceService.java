package nl.hauntedmc.dataregistry.core.population;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationOrdinalQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationResolvedGamemode;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.api.population.PopulationScopeType;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionCause;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionType;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPopulationMembershipEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationScopeStateEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Administrative and recovery maintenance for derived population aggregates. */
public final class PopulationMaintenanceService {

    private final DataRegistry dataRegistry;

    public PopulationMaintenanceService(DataRegistry dataRegistry) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
    }

    public PopulationReconciliationResult reconcileOnlineState() {
        if (!dataRegistry.isFeatureEnabled(DataRegistryFeature.POPULATION)) {
            return new PopulationReconciliationResult(0, 0);
        }
        return dataRegistry.getORM().runInTransaction(session -> {
            Instant now = Instant.now();
            PopulationScope networkScope = PopulationScope.network();
            PopulationScopeStateEntity networkState = PopulationPersistence.ensureAndLockScopeState(
                    session,
                    networkScope,
                    PopulationBaselineQuality.TRACKED_ONLY,
                    PopulationBaselineQuality.TRACKED_ONLY,
                    now
            );

            List<PlayerOnlineStatusEntity> online = session.createQuery(
                            "SELECT s FROM PlayerOnlineStatusEntity s JOIN FETCH s.player " +
                                    "WHERE s.online = true ORDER BY s.player.id ASC",
                            PlayerOnlineStatusEntity.class
                    )
                    .list();
            Map<PopulationScope, List<PlayerOnlineStatusEntity>> onlineByScope = new HashMap<>();
            onlineByScope.put(networkScope, new ArrayList<>(online));
            for (PlayerOnlineStatusEntity status : online) {
                PopulationResolvedGamemode resolved = dataRegistry.resolvePopulationGamemode(status.getCurrentServer());
                if (resolved.tracked() && resolved.gamemodeKey() != null) {
                    onlineByScope.computeIfAbsent(
                            PopulationScope.gamemode(resolved.gamemodeKey()),
                            ignored -> new ArrayList<>()
                    ).add(status);
                }
            }

            List<PopulationScopeStateEntity> existingStates = session.createQuery(
                    "SELECT s FROM PopulationScopeStateEntity s",
                    PopulationScopeStateEntity.class
            ).list();
            for (PopulationScopeStateEntity existing : existingStates) {
                onlineByScope.putIfAbsent(
                        new PopulationScope(existing.getScopeType(), existing.getScopeKey()),
                        new ArrayList<>()
                );
            }

            List<PopulationScope> scopes = onlineByScope.keySet().stream()
                    .sorted(Comparator
                            .comparingInt((PopulationScope scope) ->
                                    scope.type() == PopulationScopeType.NETWORK ? 0 : 1)
                            .thenComparing(PopulationScope::storageKey))
                    .toList();

            int changed = 0;
            int peaks = 0;
            for (PopulationScope scope : scopes) {
                PopulationScopeStateEntity state = scope.type() == PopulationScopeType.NETWORK
                        ? networkState
                        : PopulationPersistence.ensureAndLockScopeState(
                                session,
                                scope,
                                networkState.getMembershipBaselineQuality(),
                                networkState.getPeakBaselineQuality(),
                                now
                        );
                List<PlayerOnlineStatusEntity> scopeOnline = onlineByScope.get(scope);
                for (PlayerOnlineStatusEntity status : scopeOnline) {
                    ensureReconciledMembership(session, state, scope, status, now);
                }

                long target = scopeOnline.size();
                long previous = state.getCurrentOnline();
                if (target != previous) {
                    state.setCurrentOnline(target);
                    state.setUpdatedAt(now);
                    PopulationPersistence.transition(
                            session,
                            PopulationTransitionType.ONLINE_CHANGED,
                            PopulationTransitionCause.RECONCILIATION,
                            scope,
                            null,
                            null,
                            null,
                            previous,
                            target,
                            now
                    );
                    changed++;
                }
                if (target > state.getOnlinePeak()) {
                    long previousPeak = state.getOnlinePeak();
                    state.setOnlinePeak(target);
                    state.setOnlinePeakAchievedAt(now);
                    PopulationPersistence.transition(
                            session,
                            PopulationTransitionType.ONLINE_PEAK_CHANGED,
                            PopulationTransitionCause.RECONCILIATION,
                            scope,
                            null,
                            null,
                            null,
                            previousPeak,
                            target,
                            now
                    );
                    peaks++;
                }
            }
            return new PopulationReconciliationResult(changed, peaks);
        });
    }

    public void setMembershipBaselineQuality(PopulationScope scope, PopulationBaselineQuality quality) {
        updateQuality(scope, quality, true);
    }

    public void setPeakBaselineQuality(PopulationScope scope, PopulationBaselineQuality quality) {
        updateQuality(scope, quality, false);
    }

    public void seedOnlinePeak(
            PopulationScope scope,
            long historicalPeak,
            Instant achievedAt,
            boolean markVerified
    ) {
        Objects.requireNonNull(scope, "scope must not be null");
        if (historicalPeak < 0L) {
            throw new IllegalArgumentException("historicalPeak must not be negative.");
        }
        dataRegistry.getORM().runInTransaction(session -> {
            Instant now = Instant.now();
            PopulationScopeStateEntity state = PopulationPersistence.ensureAndLockScopeState(
                    session,
                    scope,
                    inheritedQuality(session, true),
                    inheritedQuality(session, false),
                    now
            );
            if (historicalPeak < state.getCurrentOnline() || historicalPeak < state.getOnlinePeak()) {
                throw new IllegalArgumentException("Historical peak cannot lower current or existing peak state.");
            }
            if (historicalPeak > state.getOnlinePeak()) {
                state.setOnlinePeak(historicalPeak);
                state.setOnlinePeakAchievedAt(achievedAt);
                state.setUpdatedAt(now);
            }
            if (markVerified) {
                state.setPeakBaselineQuality(PopulationBaselineQuality.VERIFIED);
            }
            return null;
        });
    }

    private static void ensureReconciledMembership(
            org.hibernate.Session session,
            PopulationScopeStateEntity state,
            PopulationScope scope,
            PlayerOnlineStatusEntity status,
            Instant now
    ) {
        if (PopulationPersistence.findMembership(session, status.getPlayer().getId(), scope) != null) {
            return;
        }
        long previousCount = state.getUniquePlayerCount();
        long ordinal = Math.addExact(previousCount, 1L);
        PlayerPopulationMembershipEntity membership = new PlayerPopulationMembershipEntity();
        membership.setPlayer(status.getPlayer());
        membership.setScopeId(scope.storageKey());
        membership.setScopeType(scope.type());
        membership.setScopeKey(scope.key());
        membership.setOrdinal(ordinal);
        membership.setOrdinalQuality(PopulationOrdinalQuality.BACKFILLED_DETERMINISTIC);
        membership.setCreatedAt(now);
        session.persist(membership);
        state.setUniquePlayerCount(ordinal);
        state.setMembershipBaselineQuality(PopulationBaselineQuality.TRACKED_ONLY);
        state.setPeakBaselineQuality(PopulationBaselineQuality.TRACKED_ONLY);
        state.setUpdatedAt(now);
        PopulationPersistence.transition(
                session,
                PopulationTransitionType.MEMBERSHIP_ADDED,
                PopulationTransitionCause.RECONCILIATION,
                scope,
                status.getPlayer().getId(),
                status.getCurrentServer(),
                ordinal,
                previousCount,
                ordinal,
                now
        );
    }

    private void updateQuality(PopulationScope scope, PopulationBaselineQuality quality, boolean membership) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(quality, "quality must not be null");
        dataRegistry.getORM().runInTransaction(session -> {
            Instant now = Instant.now();
            PopulationScopeStateEntity state = PopulationPersistence.ensureAndLockScopeState(
                    session,
                    scope,
                    inheritedQuality(session, true),
                    inheritedQuality(session, false),
                    now
            );
            if (membership) {
                state.setMembershipBaselineQuality(quality);
            } else {
                state.setPeakBaselineQuality(quality);
            }
            state.setUpdatedAt(now);
            return null;
        });
    }

    private static PopulationBaselineQuality inheritedQuality(org.hibernate.Session session, boolean membership) {
        PopulationScopeStateEntity network = session.find(
                PopulationScopeStateEntity.class,
                PopulationScope.network().storageKey()
        );
        if (network == null) {
            return PopulationBaselineQuality.TRACKED_ONLY;
        }
        return membership ? network.getMembershipBaselineQuality() : network.getPeakBaselineQuality();
    }
}
