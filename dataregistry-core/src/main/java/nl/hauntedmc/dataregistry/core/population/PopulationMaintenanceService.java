package nl.hauntedmc.dataregistry.core.population;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationResolvedGamemode;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionCause;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionType;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationScopeStateEntity;

import java.time.Instant;
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
            List<PlayerOnlineStatusEntity> online = session.createQuery(
                            "SELECT s FROM PlayerOnlineStatusEntity s WHERE s.online = true",
                            PlayerOnlineStatusEntity.class
                    )
                    .list();
            Map<PopulationScope, Long> counts = new HashMap<>();
            counts.put(PopulationScope.network(), (long) online.size());
            for (PlayerOnlineStatusEntity status : online) {
                PopulationResolvedGamemode resolved = dataRegistry.resolvePopulationGamemode(status.getCurrentServer());
                if (resolved.tracked() && resolved.gamemodeKey() != null) {
                    counts.merge(PopulationScope.gamemode(resolved.gamemodeKey()), 1L, Long::sum);
                }
            }

            List<PopulationScopeStateEntity> existingStates = session.createQuery(
                    "SELECT s FROM PopulationScopeStateEntity s",
                    PopulationScopeStateEntity.class
            ).list();
            for (PopulationScopeStateEntity existing : existingStates) {
                counts.putIfAbsent(new PopulationScope(existing.getScopeType(), existing.getScopeKey()), 0L);
            }

            int changed = 0;
            int peaks = 0;
            for (Map.Entry<PopulationScope, Long> entry : counts.entrySet()) {
                PopulationScope scope = entry.getKey();
                PopulationScopeStateEntity state = PopulationPersistence.ensureAndLockScopeState(
                        session,
                        scope,
                        inheritedQuality(session, true),
                        inheritedQuality(session, false),
                        now
                );
                long target = entry.getValue();
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
