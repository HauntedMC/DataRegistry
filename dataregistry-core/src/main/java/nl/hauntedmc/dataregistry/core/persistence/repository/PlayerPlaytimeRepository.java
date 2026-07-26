package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.api.playtime.PlayerGamemodePlaytimeSnapshot;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.query.NativeQuery;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PlayerPlaytimeRepository extends AbstractRepository<PlayerPlaytimeEntity, Long> {

    private final Set<String> defaultExcludedGamemodeKeys;

    public PlayerPlaytimeRepository(ORMContext ormContext) {
        this(ormContext, Set.of());
    }

    public PlayerPlaytimeRepository(ORMContext ormContext, Collection<String> defaultExcludedGamemodeKeys) {
        super(ormContext, PlayerPlaytimeEntity.class);
        Objects.requireNonNull(defaultExcludedGamemodeKeys, "defaultExcludedGamemodeKeys must not be null");
        LinkedHashSet<String> normalizedExcludedGamemodes = new LinkedHashSet<>();
        for (String gamemodeKey : defaultExcludedGamemodeKeys) {
            String normalized = normalizeGamemodeKey(gamemodeKey);
            if (normalized != null) {
                normalizedExcludedGamemodes.add(normalized);
            }
        }
        this.defaultExcludedGamemodeKeys = Set.copyOf(normalizedExcludedGamemodes);
    }

    public Optional<PlayerPlaytimeEntity> findByPlayerAndGamemode(Long playerId, String gamemodeKey) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        String normalizedGamemodeKey = requireNormalizedGamemodeKey(gamemodeKey);
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM PlayerPlaytimeEntity p " +
                                        "WHERE p.player.id = :playerId AND p.gamemodeKey = :gamemodeKey",
                                PlayerPlaytimeEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setParameter("gamemodeKey", normalizedGamemodeKey)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    public List<PlayerPlaytimeEntity> findByPlayer(Long playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM PlayerPlaytimeEntity p " +
                                        "WHERE p.player.id = :playerId " +
                                        "ORDER BY p.trackedMillis DESC, p.gamemodeKey ASC",
                                PlayerPlaytimeEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .list()
        );
    }

    public List<PlayerPlaytimeEntity> findTopByGamemode(String gamemodeKey, int limit) {
        String normalizedGamemodeKey = requireNormalizedGamemodeKey(gamemodeKey);
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM PlayerPlaytimeEntity p " +
                                        "WHERE p.gamemodeKey = :gamemodeKey " +
                                        "ORDER BY p.trackedMillis DESC, p.player.username ASC",
                                PlayerPlaytimeEntity.class
                        )
                        .setParameter("gamemodeKey", normalizedGamemodeKey)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public Optional<PlayerPlaytimeSnapshot> findSnapshotByPlayerId(Long playerId) {
        return findSnapshotByPlayerId(playerId, Instant.now(), defaultExcludedGamemodeKeys);
    }

    public Optional<PlayerPlaytimeSnapshot> findSnapshotByPlayerId(Long playerId, Instant asOf) {
        return findSnapshotByPlayerId(playerId, asOf, defaultExcludedGamemodeKeys);
    }

    public Optional<PlayerPlaytimeSnapshot> findSnapshotByPlayerId(
            Long playerId,
            Instant asOf,
            Collection<String> excludedGamemodeKeys
    ) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");
        Set<String> normalizedExcludedGamemodes = normalizeGamemodeKeys(excludedGamemodeKeys);
        return ormContext.runInTransaction(session -> {
            PlayerEntity player = session.find(PlayerEntity.class, playerId);
            if (player == null) {
                return Optional.empty();
            }

            List<PlayerPlaytimeEntity> aggregates = session.createQuery(
                            "SELECT p FROM PlayerPlaytimeEntity p " +
                                    "WHERE p.player.id = :playerId " +
                                    "ORDER BY p.trackedMillis DESC, p.gamemodeKey ASC",
                            PlayerPlaytimeEntity.class
                    )
                    .setParameter("playerId", playerId)
                    .list();

            Optional<PlayerPlaytimeSegmentEntity> openSegment = findLiveOpenSegmentForPlayer(session, playerId);

            Map<String, GamemodeSnapshotAccumulator> byGamemode = new LinkedHashMap<>();
            long trackedTotalMillis = 0L;
            long networkTotalMillis = 0L;

            for (PlayerPlaytimeEntity aggregate : aggregates) {
                boolean counted = !normalizedExcludedGamemodes.contains(aggregate.getGamemodeKey());
                trackedTotalMillis += aggregate.getTrackedMillis();
                if (counted) {
                    networkTotalMillis += aggregate.getTrackedMillis();
                }
                byGamemode.put(
                        aggregate.getGamemodeKey(),
                        new GamemodeSnapshotAccumulator(
                                aggregate.getGamemodeKey(),
                                aggregate.getTrackedMillis(),
                                counted,
                                false,
                                null,
                                null,
                                aggregate.getFirstTrackedAt(),
                                aggregate.getLastTrackedAt(),
                                aggregate.getSegmentCount()
                        )
                );
            }

            if (openSegment.isPresent() && isLiveSegment(openSegment.get(), asOf)) {
                PlayerPlaytimeSegmentEntity segment = openSegment.get();
                long liveDeltaMillis = computeLiveDeltaMillis(segment.getLastAccruedAt(), asOf);
                boolean counted = !normalizedExcludedGamemodes.contains(segment.getGamemodeKey());
                if (liveDeltaMillis > 0L) {
                    trackedTotalMillis += liveDeltaMillis;
                    if (counted) {
                        networkTotalMillis += liveDeltaMillis;
                    }
                }

                GamemodeSnapshotAccumulator accumulator = byGamemode.computeIfAbsent(
                        segment.getGamemodeKey(),
                        key -> new GamemodeSnapshotAccumulator(
                                key,
                                0L,
                                counted,
                                false,
                                null,
                                null,
                                segment.getStartedAt(),
                                segment.getStartedAt(),
                                1L
                        )
                );
                accumulator.trackedMillis += liveDeltaMillis;
                accumulator.countedTowardsNetworkTotal = counted;
                accumulator.active = true;
                accumulator.activeSince = segment.getStartedAt();
                accumulator.activeServerName = segment.getLastServer();
                accumulator.firstTrackedAt = minInstant(accumulator.firstTrackedAt, segment.getStartedAt());
                accumulator.lastTrackedAt = maxInstant(accumulator.lastTrackedAt, asOf);
            }

            List<PlayerGamemodePlaytimeSnapshot> gamemodeSnapshots = byGamemode.values().stream()
                    .sorted(Comparator
                            .comparingLong(GamemodeSnapshotAccumulator::trackedMillis).reversed()
                            .thenComparing(GamemodeSnapshotAccumulator::gamemodeKey))
                    .map(GamemodeSnapshotAccumulator::toSnapshot)
                    .toList();

            return Optional.of(new PlayerPlaytimeSnapshot(
                    player.getId(),
                    player.getUuid(),
                    player.getUsername(),
                    trackedTotalMillis,
                    networkTotalMillis,
                    asOf,
                    gamemodeSnapshots
            ));
        });
    }

    public Optional<PlayerPlaytimeSnapshot> findSnapshotByPlayerUuid(String playerUuid) {
        return findSnapshotByPlayerUuid(playerUuid, Instant.now(), defaultExcludedGamemodeKeys);
    }

    public Optional<PlayerPlaytimeSnapshot> findSnapshotByPlayerUuid(
            String playerUuid,
            Instant asOf,
            Collection<String> excludedGamemodeKeys
    ) {
        String normalizedPlayerUuid = normalizeUuid(playerUuid);
        if (normalizedPlayerUuid == null) {
            return Optional.empty();
        }
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p.id FROM PlayerEntity p WHERE p.uuid = :uuid",
                                Long.class
                        )
                        .setParameter("uuid", normalizedPlayerUuid)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        ).flatMap(playerId -> findSnapshotByPlayerId(playerId, asOf, excludedGamemodeKeys));
    }

    public List<PlayerPlaytimeLeaderboardEntry> findTopPlayersByGamemode(String gamemodeKey, int limit) {
        String normalizedGamemodeKey = requireNormalizedGamemodeKey(gamemodeKey);
        int resultLimit = Math.max(1, limit);
        Instant generatedAt = Instant.now();
        return ormContext.runInTransaction(session -> {
            NativeQuery<Object[]> query = session.createNativeQuery(
                    "SELECT totals.player_id, p.uuid, p.username, SUM(totals.tracked_millis) AS tracked_millis " +
                            "FROM (" +
                            "SELECT t.player_id, t.tracked_millis FROM player_playtime t " +
                            "WHERE t.gamemode_key = :gamemodeKey " +
                            "UNION ALL " +
                            "SELECT s.player_id, GREATEST(0, TIMESTAMPDIFF(MICROSECOND, s.last_accrued_at, :asOf) " +
                            "DIV 1000) FROM player_playtime_segments s " +
                            "INNER JOIN player_sessions ps ON ps.id = s.session_id " +
                            "WHERE s.gamemode_key = :gamemodeKey AND s.ended_at IS NULL AND ps.ended_at IS NULL" +
                            ") totals INNER JOIN player_entity p ON p.id = totals.player_id " +
                            "GROUP BY totals.player_id, p.uuid, p.username " +
                            "HAVING SUM(totals.tracked_millis) > 0 " +
                            "ORDER BY tracked_millis DESC, LOWER(p.username) ASC, totals.player_id ASC"
            );
            query.setParameter("gamemodeKey", normalizedGamemodeKey);
            query.setParameter("asOf", generatedAt);
            query.setMaxResults(resultLimit);
            return toLeaderboardEntries(query.list(), generatedAt);
        });
    }

    public List<PlayerPlaytimeLeaderboardEntry> findTopPlayersByNetworkTotal(int limit) {
        return findTopPlayersByNetworkTotal(limit, defaultExcludedGamemodeKeys);
    }

    public List<PlayerPlaytimeLeaderboardEntry> findTopPlayersByNetworkTotal(
            int limit,
            Collection<String> excludedGamemodeKeys
    ) {
        int resultLimit = Math.max(1, limit);
        Set<String> normalizedExcludedGamemodes = normalizeGamemodeKeys(excludedGamemodeKeys);
        Instant generatedAt = Instant.now();
        return ormContext.runInTransaction(session -> {
            List<String> excludedGamemodes = normalizedExcludedGamemodes.stream().sorted().toList();
            String aggregateExclusion = exclusionClause("t.gamemode_key", excludedGamemodes, " WHERE ");
            String segmentExclusion = exclusionClause("s.gamemode_key", excludedGamemodes, " AND ");
            NativeQuery<Object[]> query = session.createNativeQuery(
                    "SELECT totals.player_id, p.uuid, p.username, SUM(totals.tracked_millis) AS tracked_millis " +
                            "FROM (" +
                            "SELECT t.player_id, t.tracked_millis FROM player_playtime t" + aggregateExclusion +
                            " UNION ALL " +
                            "SELECT s.player_id, GREATEST(0, TIMESTAMPDIFF(MICROSECOND, s.last_accrued_at, :asOf) " +
                            "DIV 1000) FROM player_playtime_segments s " +
                            "INNER JOIN player_sessions ps ON ps.id = s.session_id " +
                            "WHERE s.ended_at IS NULL AND ps.ended_at IS NULL" + segmentExclusion +
                            ") totals INNER JOIN player_entity p ON p.id = totals.player_id " +
                            "GROUP BY totals.player_id, p.uuid, p.username " +
                            "HAVING SUM(totals.tracked_millis) > 0 " +
                            "ORDER BY tracked_millis DESC, LOWER(p.username) ASC, totals.player_id ASC"
            );
            query.setParameter("asOf", generatedAt);
            for (int index = 0; index < excludedGamemodes.size(); index++) {
                query.setParameter("excludedGamemode" + index, excludedGamemodes.get(index));
            }
            query.setMaxResults(resultLimit);
            return toLeaderboardEntries(query.list(), generatedAt);
        });
    }

    public List<String> findTrackedGamemodeKeys() {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT DISTINCT p.gamemodeKey FROM PlayerPlaytimeEntity p ORDER BY p.gamemodeKey ASC",
                                String.class
                        )
                        .list()
        );
    }

    private static Optional<PlayerPlaytimeSegmentEntity> findLiveOpenSegmentForPlayer(
            org.hibernate.Session session,
            Long playerId
    ) {
        return session.createQuery(
                        "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                "WHERE s.player.id = :playerId " +
                                "AND s.endedAt IS NULL AND s.session.endedAt IS NULL " +
                                "ORDER BY s.startedAt DESC, s.id DESC",
                        PlayerPlaytimeSegmentEntity.class
                )
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    private static List<PlayerPlaytimeLeaderboardEntry> toLeaderboardEntries(
            List<Object[]> rows,
            Instant generatedAt
    ) {
        List<PlayerPlaytimeLeaderboardEntry> entries = new ArrayList<>(rows.size());
        long rank = 1L;
        for (Object[] row : rows) {
            if (row == null || row.length < 4 || !(row[0] instanceof Number playerId)
                    || !(row[3] instanceof Number trackedMillis) || trackedMillis.longValue() <= 0L) {
                continue;
            }
            entries.add(new PlayerPlaytimeLeaderboardEntry(
                rank++,
                    playerId.longValue(),
                    row[1] == null ? "" : row[1].toString(),
                    row[2] == null ? "" : row[2].toString(),
                    trackedMillis.longValue(),
                    generatedAt
            ));
        }
        return entries;
    }

    private static String exclusionClause(String column, List<String> values, String prefix) {
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder placeholders = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                placeholders.append(", ");
            }
            placeholders.append(":excludedGamemode").append(index);
        }
        return prefix + column + " NOT IN (" + placeholders + ")";
    }

    private static boolean isLiveSegment(PlayerPlaytimeSegmentEntity segment, Instant asOf) {
        return segment.getEndedAt() == null
                && segment.getSession() != null
                && segment.getSession().getEndedAt() == null
                && !asOf.isBefore(segment.getLastAccruedAt());
    }

    private static long computeLiveDeltaMillis(Instant lastAccruedAt, Instant asOf) {
        if (lastAccruedAt == null || asOf.isBefore(lastAccruedAt)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(lastAccruedAt, asOf).toMillis());
    }

    private static Set<String> normalizeGamemodeKeys(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedValue = normalizeGamemodeKey(value);
            if (normalizedValue != null) {
                normalized.add(normalizedValue);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String requireNormalizedGamemodeKey(String value) {
        String normalized = normalizeGamemodeKey(value);
        if (normalized == null) {
            throw new IllegalArgumentException("gamemodeKey must not be blank");
        }
        return normalized;
    }

    private static String normalizeGamemodeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeUuid(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(trimmed).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Instant minInstant(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private static Instant maxInstant(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static final class GamemodeSnapshotAccumulator {
        private final String gamemodeKey;
        private long trackedMillis;
        private boolean countedTowardsNetworkTotal;
        private boolean active;
        private Instant activeSince;
        private String activeServerName;
        private Instant firstTrackedAt;
        private Instant lastTrackedAt;
        private long segmentCount;

        private GamemodeSnapshotAccumulator(
                String gamemodeKey,
                long trackedMillis,
                boolean countedTowardsNetworkTotal,
                boolean active,
                Instant activeSince,
                String activeServerName,
                Instant firstTrackedAt,
                Instant lastTrackedAt,
                long segmentCount
        ) {
            this.gamemodeKey = gamemodeKey;
            this.trackedMillis = trackedMillis;
            this.countedTowardsNetworkTotal = countedTowardsNetworkTotal;
            this.active = active;
            this.activeSince = activeSince;
            this.activeServerName = activeServerName;
            this.firstTrackedAt = firstTrackedAt;
            this.lastTrackedAt = lastTrackedAt;
            this.segmentCount = segmentCount;
        }

        private String gamemodeKey() {
            return gamemodeKey;
        }

        private long trackedMillis() {
            return trackedMillis;
        }

        private PlayerGamemodePlaytimeSnapshot toSnapshot() {
            return new PlayerGamemodePlaytimeSnapshot(
                    gamemodeKey,
                    trackedMillis,
                    countedTowardsNetworkTotal,
                    active,
                    activeSince,
                    activeServerName,
                    firstTrackedAt,
                    lastTrackedAt,
                    segmentCount
            );
        }
    }

}
