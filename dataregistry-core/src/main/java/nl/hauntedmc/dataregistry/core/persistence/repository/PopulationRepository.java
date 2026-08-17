package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.population.PlayerPopulationMembership;
import nl.hauntedmc.dataregistry.api.population.PopulationJoinContext;
import nl.hauntedmc.dataregistry.api.population.PopulationResolvedGamemode;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.api.population.PopulationScopeType;
import nl.hauntedmc.dataregistry.api.population.PopulationSnapshot;
import nl.hauntedmc.dataregistry.api.population.PopulationTransition;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionBatch;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionQuery;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPopulationMembershipEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionVisitEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationScopeStateEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationTransitionEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-side repository for canonical population state and its durable transition journal. */
public final class PopulationRepository extends AbstractRepository<PopulationScopeStateEntity, String> {

    public PopulationRepository(ORMContext ormContext) {
        super(ormContext, PopulationScopeStateEntity.class);
    }

    public Optional<PopulationSnapshot> findSnapshot(PopulationScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        Instant generatedAt = Instant.now();
        return ormContext.runInTransaction(session -> Optional.ofNullable(
                session.find(PopulationScopeStateEntity.class, scope.storageKey())
        ).map(state -> toSnapshot(state, generatedAt)));
    }

    public List<PopulationSnapshot> findGamemodeSnapshots() {
        Instant generatedAt = Instant.now();
        return ormContext.runInTransaction(session -> session.createQuery(
                        "SELECT s FROM PopulationScopeStateEntity s WHERE s.scopeType = :type ORDER BY s.scopeKey ASC",
                        PopulationScopeStateEntity.class
                )
                .setParameter("type", PopulationScopeType.GAMEMODE)
                .list().stream()
                .map(state -> toSnapshot(state, generatedAt))
                .toList());
    }

    public Optional<PlayerPopulationMembership> findMembership(long playerId, PopulationScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return ormContext.runInTransaction(session -> session.createQuery(
                        "SELECT m FROM PlayerPopulationMembershipEntity m " +
                                "JOIN FETCH m.player WHERE m.player.id = :playerId AND m.scopeId = :scopeId",
                        PlayerPopulationMembershipEntity.class
                )
                .setParameter("playerId", playerId)
                .setParameter("scopeId", scope.storageKey())
                .setMaxResults(1)
                .uniqueResultOptional()
                .map(PopulationRepository::toMembership));
    }

    public List<PlayerPopulationMembership> findMemberships(long playerId) {
        return ormContext.runInTransaction(session -> session.createQuery(
                        "SELECT m FROM PlayerPopulationMembershipEntity m JOIN FETCH m.player " +
                                "WHERE m.player.id = :playerId ORDER BY m.scopeType ASC, m.scopeKey ASC",
                        PlayerPopulationMembershipEntity.class
                )
                .setParameter("playerId", playerId)
                .list().stream()
                .map(PopulationRepository::toMembership)
                .toList());
    }

    public Optional<PopulationJoinContext> findJoinContext(
            UUID playerUuid,
            String requestedServer,
            PopulationResolvedGamemode resolvedGamemode
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        Objects.requireNonNull(resolvedGamemode, "resolvedGamemode must not be null");
        String normalizedServer = normalizeServer(requestedServer);
        if (normalizedServer == null) {
            return Optional.empty();
        }
        Instant generatedAt = Instant.now();
        return ormContext.runInTransaction(session -> {
            PlayerEntity player = session.createQuery(
                            "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid",
                            PlayerEntity.class
                    )
                    .setParameter("uuid", playerUuid.toString())
                    .setMaxResults(1)
                    .uniqueResult();
            if (player == null) {
                return Optional.empty();
            }
            PlayerOnlineStatusEntity status = session.find(PlayerOnlineStatusEntity.class, player.getId());
            if (status == null || !status.isOnline() || !normalizedServer.equals(normalizeServer(status.getCurrentServer()))) {
                return Optional.empty();
            }
            PlayerSessionEntity activeSession = session.createQuery(
                            "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                    "ORDER BY s.startedAt DESC, s.id DESC",
                            PlayerSessionEntity.class
                    )
                    .setParameter("playerId", player.getId())
                    .setMaxResults(1)
                    .uniqueResult();
            if (activeSession == null) {
                return Optional.empty();
            }
            PlayerSessionVisitEntity activeVisit = session.createQuery(
                            "SELECT v FROM PlayerSessionVisitEntity v WHERE v.player.id = :playerId AND v.leftAt IS NULL " +
                                    "ORDER BY v.enteredAt DESC, v.id DESC",
                            PlayerSessionVisitEntity.class
                    )
                    .setParameter("playerId", player.getId())
                    .setMaxResults(1)
                    .uniqueResult();
            if (activeVisit == null || !normalizedServer.equals(normalizeServer(activeVisit.getServerName()))) {
                return Optional.empty();
            }

            PopulationScope networkScope = PopulationScope.network();
            PlayerPopulationMembershipEntity networkMembership = membership(session, player.getId(), networkScope);
            PopulationScopeStateEntity networkState = session.find(PopulationScopeStateEntity.class, networkScope.storageKey());
            if (networkMembership == null || networkState == null) {
                return Optional.empty();
            }

            Optional<String> gamemodeKey = resolvedGamemode.tracked() && resolvedGamemode.gamemodeKey() != null
                    ? Optional.of(resolvedGamemode.gamemodeKey())
                    : Optional.empty();
            Optional<PlayerPopulationMembership> gamemodeMembership = Optional.empty();
            Optional<PopulationSnapshot> gamemodeSnapshot = Optional.empty();
            boolean gamemodeFirstJoin = false;
            if (gamemodeKey.isPresent()) {
                PopulationScope scope = PopulationScope.gamemode(gamemodeKey.get());
                PlayerPopulationMembershipEntity membership = membership(session, player.getId(), scope);
                PopulationScopeStateEntity state = session.find(PopulationScopeStateEntity.class, scope.storageKey());
                if (membership != null) {
                    gamemodeMembership = Optional.of(toMembership(membership));
                    gamemodeFirstJoin = Objects.equals(membership.getFirstVisitId(), activeVisit.getId());
                }
                if (state != null) {
                    gamemodeSnapshot = Optional.of(toSnapshot(state, generatedAt));
                }
            }

            PlayerIdentity identity = PlayerRepository.toIdentity(player);
            return Optional.of(new PopulationJoinContext(
                    identity,
                    normalizedServer,
                    gamemodeKey,
                    Objects.equals(networkMembership.getFirstSessionId(), activeSession.getId()),
                    gamemodeFirstJoin,
                    toMembership(networkMembership),
                    gamemodeMembership,
                    toSnapshot(networkState, generatedAt),
                    gamemodeSnapshot,
                    generatedAt
            ));
        });
    }

    public PopulationTransitionBatch findTransitions(PopulationTransitionQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Instant generatedAt = Instant.now();
        return ormContext.runInTransaction(session -> {
            Long earliest = session.createQuery("SELECT MIN(t.id) FROM PopulationTransitionEntity t", Long.class)
                    .getSingleResult();
            Long latest = session.createQuery("SELECT MAX(t.id) FROM PopulationTransitionEntity t", Long.class)
                    .getSingleResult();

            StringBuilder hql = new StringBuilder(
                    "SELECT t FROM PopulationTransitionEntity t WHERE t.id > :afterId"
            );
            if (query.scope() != null) {
                hql.append(" AND t.scopeId = :scopeId");
            }
            if (!query.types().isEmpty()) {
                hql.append(" AND t.transitionType IN :types");
            }
            if (!query.causes().isEmpty()) {
                hql.append(" AND t.transitionCause IN :causes");
            }
            hql.append(" ORDER BY t.id ASC");
            var typedQuery = session.createQuery(hql.toString(), PopulationTransitionEntity.class)
                    .setParameter("afterId", query.afterId())
                    .setMaxResults(query.limit());
            if (query.scope() != null) {
                typedQuery.setParameter("scopeId", query.scope().storageKey());
            }
            if (!query.types().isEmpty()) {
                typedQuery.setParameter("types", query.types());
            }
            if (!query.causes().isEmpty()) {
                typedQuery.setParameter("causes", query.causes());
            }
            List<PopulationTransition> transitions = typedQuery.list().stream()
                    .map(PopulationRepository::toTransition)
                    .toList();
            return new PopulationTransitionBatch(
                    earliest == null ? 0L : earliest,
                    latest == null ? 0L : latest,
                    transitions,
                    generatedAt
            );
        });
    }

    public int deleteTransitionsBefore(Instant cutoff, int batchSize) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive.");
        }
        return ormContext.runInTransaction(session -> {
            List<Long> ids = session.createQuery(
                            "SELECT t.id FROM PopulationTransitionEntity t " +
                                    "WHERE t.occurredAt < :cutoff ORDER BY t.id ASC",
                            Long.class
                    )
                    .setParameter("cutoff", cutoff)
                    .setMaxResults(batchSize)
                    .list();
            if (ids.isEmpty()) {
                return 0;
            }
            return session.createMutationQuery("DELETE FROM PopulationTransitionEntity t WHERE t.id IN :ids")
                    .setParameter("ids", ids)
                    .executeUpdate();
        });
    }

    private static PlayerPopulationMembershipEntity membership(
            org.hibernate.Session session,
            long playerId,
            PopulationScope scope
    ) {
        return session.createQuery(
                        "SELECT m FROM PlayerPopulationMembershipEntity m JOIN FETCH m.player " +
                                "WHERE m.player.id = :playerId AND m.scopeId = :scopeId",
                        PlayerPopulationMembershipEntity.class
                )
                .setParameter("playerId", playerId)
                .setParameter("scopeId", scope.storageKey())
                .setMaxResults(1)
                .uniqueResult();
    }

    public static PopulationSnapshot toSnapshot(PopulationScopeStateEntity state, Instant generatedAt) {
        return new PopulationSnapshot(
                new PopulationScope(state.getScopeType(), state.getScopeKey()),
                state.getUniquePlayerCount(),
                state.getCurrentOnline(),
                state.getOnlinePeak(),
                state.getOnlinePeakAchievedAt(),
                state.getMembershipBaselineQuality(),
                state.getPeakBaselineQuality(),
                generatedAt
        );
    }

    public static PlayerPopulationMembership toMembership(PlayerPopulationMembershipEntity entity) {
        PlayerEntity player = entity.getPlayer();
        return new PlayerPopulationMembership(
                player.getId(),
                UUID.fromString(player.getUuid()),
                player.getUsername(),
                new PopulationScope(entity.getScopeType(), entity.getScopeKey()),
                entity.getOrdinal(),
                entity.getOrdinalQuality(),
                entity.getFirstJoinedAt(),
                entity.getCreatedAt()
        );
    }

    private static PopulationTransition toTransition(PopulationTransitionEntity entity) {
        return new PopulationTransition(
                entity.getId(),
                entity.getTransitionType(),
                entity.getTransitionCause(),
                new PopulationScope(entity.getScopeType(), entity.getScopeKey()),
                entity.getPlayerId(),
                entity.getServerName(),
                entity.getOrdinal(),
                entity.getPreviousValue(),
                entity.getCurrentValue(),
                entity.getOccurredAt()
        );
    }

    private static String normalizeServer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
