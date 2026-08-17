package nl.hauntedmc.dataregistry.core.population;

import jakarta.persistence.LockModeType;
import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionCause;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionType;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPopulationMembershipEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationScopeStateEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationTransitionEntity;
import org.hibernate.Session;

import java.time.Instant;
import java.util.Objects;

/** Shared transactional primitives for the population domain. */
final class PopulationPersistence {

    private PopulationPersistence() {
    }

    static PopulationScopeStateEntity ensureAndLockScopeState(
            Session session,
            PopulationScope scope,
            PopulationBaselineQuality membershipQuality,
            PopulationBaselineQuality peakQuality,
            Instant now
    ) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(membershipQuality, "membershipQuality must not be null");
        Objects.requireNonNull(peakQuality, "peakQuality must not be null");
        Objects.requireNonNull(now, "now must not be null");

        // The player-domain database is MySQL in production. INSERT IGNORE makes first-observation creation safe
        // across multiple Velocity writers; the subsequent pessimistic lock serializes ordinal/counter allocation.
        session.createNativeMutationQuery(
                        "INSERT IGNORE INTO population_scope_state " +
                                "(scope_id, scope_type, scope_key, unique_player_count, current_online, online_peak, " +
                                "membership_baseline_quality, peak_baseline_quality, backfill_version, created_at, " +
                                "updated_at, version) VALUES " +
                                "(:scopeId, :scopeType, :scopeKey, 0, 0, 0, :membershipQuality, :peakQuality, 0, " +
                                ":createdAt, :updatedAt, 0)"
                )
                .setParameter("scopeId", scope.storageKey())
                .setParameter("scopeType", scope.type().name())
                .setParameter("scopeKey", scope.key())
                .setParameter("membershipQuality", membershipQuality.name())
                .setParameter("peakQuality", peakQuality.name())
                .setParameter("createdAt", now)
                .setParameter("updatedAt", now)
                .executeUpdate();

        PopulationScopeStateEntity state = session.find(
                PopulationScopeStateEntity.class,
                scope.storageKey(),
                LockModeType.PESSIMISTIC_WRITE
        );
        if (state == null) {
            throw new IllegalStateException("Population scope state was not created for " + scope.storageKey());
        }
        return state;
    }

    static PlayerPopulationMembershipEntity findMembership(Session session, long playerId, PopulationScope scope) {
        return session.createQuery(
                        "SELECT m FROM PlayerPopulationMembershipEntity m " +
                                "WHERE m.player.id = :playerId AND m.scopeId = :scopeId",
                        PlayerPopulationMembershipEntity.class
                )
                .setParameter("playerId", playerId)
                .setParameter("scopeId", scope.storageKey())
                .setMaxResults(1)
                .uniqueResult();
    }

    static void transition(
            Session session,
            PopulationTransitionType type,
            PopulationTransitionCause cause,
            PopulationScope scope,
            Long playerId,
            String serverName,
            Long ordinal,
            long previousValue,
            long currentValue,
            Instant occurredAt
    ) {
        PopulationTransitionEntity transition = new PopulationTransitionEntity();
        transition.setTransitionType(type);
        transition.setTransitionCause(cause);
        transition.setScopeId(scope.storageKey());
        transition.setScopeType(scope.type());
        transition.setScopeKey(scope.key());
        transition.setPlayerId(playerId);
        transition.setServerName(serverName);
        transition.setOrdinal(ordinal);
        transition.setPreviousValue(previousValue);
        transition.setCurrentValue(currentValue);
        transition.setOccurredAt(occurredAt);
        transition.setCreatedAt(Instant.now());
        session.persist(transition);
    }
}
