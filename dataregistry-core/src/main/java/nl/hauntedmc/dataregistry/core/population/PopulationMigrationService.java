package nl.hauntedmc.dataregistry.core.population;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationOrdinalQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPopulationMembershipEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationScopeStateEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Idempotently reconstructs missing population membership from existing canonical DataRegistry evidence. */
public final class PopulationMigrationService {

    public static final int BACKFILL_VERSION = 1;
    private static final String ACTIVITY_TABLE = "player_activity_summary";
    private static final String SESSIONS_TABLE = "player_sessions";
    private static final String PLAYTIME_TABLE = "player_playtime";

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

            boolean networkVersionUpdated = networkState.getBackfillVersion() < BACKFILL_VERSION;
            long networkAdded = backfillNetworkMemberships(session, networkState, now);
            if (networkAdded > 0L) {
                networkState.setMembershipBaselineQuality(PopulationBaselineQuality.TRACKED_ONLY);
                networkState.setPeakBaselineQuality(PopulationBaselineQuality.TRACKED_ONLY);
            }
            if (networkVersionUpdated) {
                networkState.setBackfillVersion(BACKFILL_VERSION);
            }
            if (networkVersionUpdated || networkAdded > 0L) {
                networkState.setUpdatedAt(now);
            }

            GamemodeBackfillResult gamemode = backfillGamemodeMemberships(
                    session,
                    networkState.getMembershipBaselineQuality(),
                    now
            );
            return new PopulationMigrationResult(
                    networkAdded,
                    gamemode.added(),
                    networkState.getMembershipBaselineQuality(),
                    networkVersionUpdated || networkAdded > 0L || gamemode.applied()
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
        Map<Long, Instant> activityFirstSeen = loadActivityFirstSeen(session);
        Map<Long, Instant> sessionFirstSeen = loadSessionFirstSeen(session);
        List<HistoricalNetworkMember> historical = new ArrayList<>(players.size());
        for (PlayerEntity player : players) {
            if (PopulationPersistence.findMembership(session, player.getId(), PopulationScope.network()) != null) {
                continue;
            }
            Instant firstSeenAt = activityFirstSeen.get(player.getId());
            if (firstSeenAt == null) {
                firstSeenAt = sessionFirstSeen.get(player.getId());
            }
            historical.add(new HistoricalNetworkMember(player, firstSeenAt));
        }

        historical.sort(Comparator
                .comparing(HistoricalNetworkMember::firstSeenAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(member -> member.player().getId()));

        long added = 0L;
        for (HistoricalNetworkMember member : historical) {
            long ordinal = Math.addExact(state.getUniquePlayerCount(), 1L);
            persistBackfilledMembership(
                    session,
                    member.player(),
                    PopulationScope.network(),
                    ordinal,
                    member.firstSeenAt(),
                    now
            );
            state.setUniquePlayerCount(ordinal);
            added++;
        }
        return added;
    }

    private static Map<Long, Instant> loadActivityFirstSeen(org.hibernate.Session session) {
        return session.doReturningWork(connection -> {
            if (!tableExists(connection, ACTIVITY_TABLE)) {
                return Map.of();
            }
            Map<Long, Instant> firstSeenByPlayer = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_id, first_seen_at FROM " + ACTIVITY_TABLE
            ); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Timestamp firstSeenAt = rows.getTimestamp("first_seen_at");
                    if (firstSeenAt != null) {
                        firstSeenByPlayer.put(rows.getLong("player_id"), firstSeenAt.toInstant());
                    }
                }
            }
            return firstSeenByPlayer;
        });
    }

    private static Map<Long, Instant> loadSessionFirstSeen(org.hibernate.Session session) {
        return session.doReturningWork(connection -> {
            if (!tableExists(connection, SESSIONS_TABLE)) {
                return Map.of();
            }
            Map<Long, Instant> firstSeenByPlayer = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_id, MIN(started_at) AS first_started_at FROM " + SESSIONS_TABLE +
                            " GROUP BY player_id"
            ); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Timestamp firstStartedAt = rows.getTimestamp("first_started_at");
                    if (firstStartedAt != null) {
                        firstSeenByPlayer.put(rows.getLong("player_id"), firstStartedAt.toInstant());
                    }
                }
            }
            return firstSeenByPlayer;
        });
    }

    private static GamemodeBackfillResult backfillGamemodeMemberships(
            org.hibernate.Session session,
            PopulationBaselineQuality baselineQuality,
            Instant now
    ) {
        List<HistoricalGamemodeMember> historical = loadHistoricalGamemodeMembers(session);
        long added = 0L;
        boolean applied = false;
        String lastScopeId = null;
        PopulationScopeStateEntity state = null;
        for (HistoricalGamemodeMember member : historical) {
            PopulationScope scope = PopulationScope.gamemode(member.gamemodeKey());
            if (!scope.storageKey().equals(lastScopeId)) {
                state = PopulationPersistence.ensureAndLockScopeState(
                        session,
                        scope,
                        baselineQuality,
                        baselineQuality,
                        now
                );
                if (state.getBackfillVersion() < BACKFILL_VERSION) {
                    state.setBackfillVersion(BACKFILL_VERSION);
                    state.setUpdatedAt(now);
                    applied = true;
                }
                lastScopeId = scope.storageKey();
            }
            if (PopulationPersistence.findMembership(session, member.playerId(), scope) != null) {
                continue;
            }
            PlayerEntity player = session.find(PlayerEntity.class, member.playerId());
            if (player == null) {
                throw new IllegalStateException(
                        "Historical playtime row references missing player id " + member.playerId() + "."
                );
            }
            long ordinal = Math.addExact(state.getUniquePlayerCount(), 1L);
            persistBackfilledMembership(
                    session,
                    player,
                    scope,
                    ordinal,
                    member.firstTrackedAt(),
                    now
            );
            state.setUniquePlayerCount(ordinal);
            state.setMembershipBaselineQuality(PopulationBaselineQuality.TRACKED_ONLY);
            state.setPeakBaselineQuality(PopulationBaselineQuality.TRACKED_ONLY);
            state.setUpdatedAt(now);
            added++;
            applied = true;
        }
        return new GamemodeBackfillResult(added, applied);
    }

    private static List<HistoricalGamemodeMember> loadHistoricalGamemodeMembers(org.hibernate.Session session) {
        return session.doReturningWork(connection -> {
            if (!tableExists(connection, PLAYTIME_TABLE)) {
                return List.of();
            }
            List<HistoricalGamemodeMember> historical = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_id, gamemode_key, first_tracked_at FROM " + PLAYTIME_TABLE +
                            " ORDER BY gamemode_key ASC, first_tracked_at ASC, player_id ASC"
            ); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Timestamp firstTrackedAt = rows.getTimestamp("first_tracked_at");
                    historical.add(new HistoricalGamemodeMember(
                            rows.getLong("player_id"),
                            rows.getString("gamemode_key"),
                            firstTrackedAt == null ? null : firstTrackedAt.toInstant()
                    ));
                }
            }
            return historical;
        });
    }

    private static boolean tableExists(java.sql.Connection connection, String tableName) throws java.sql.SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(),
                null,
                tableName,
                new String[]{"TABLE"}
        )) {
            return tables.next();
        }
    }

    private static void persistBackfilledMembership(
            org.hibernate.Session session,
            PlayerEntity player,
            PopulationScope scope,
            long ordinal,
            Instant firstJoinedAt,
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
        membership.setCreatedAt(createdAt);
        session.persist(membership);
    }

    private record HistoricalNetworkMember(PlayerEntity player, Instant firstSeenAt) {
    }

    private record HistoricalGamemodeMember(long playerId, String gamemodeKey, Instant firstTrackedAt) {
    }

    private record GamemodeBackfillResult(long added, boolean applied) {
    }
}
