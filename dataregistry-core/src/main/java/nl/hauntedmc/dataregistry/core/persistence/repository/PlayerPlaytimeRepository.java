package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.api.playtime.PlayerGamemodePlaytimeSnapshot;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

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
            Map<Long, LeaderboardAccumulator> byPlayer = new LinkedHashMap<>();
            List<PlayerPlaytimeEntity> aggregates = session.createQuery(
                            "SELECT t FROM PlayerPlaytimeEntity t " +
                                    "WHERE t.gamemodeKey = :gamemodeKey",
                            PlayerPlaytimeEntity.class
                    )
                    .setParameter("gamemodeKey", normalizedGamemodeKey)
                    .list();
            for (PlayerPlaytimeEntity aggregate : aggregates) {
                accumulateAggregate(byPlayer, aggregate, generatedAt);
            }

            List<PlayerPlaytimeSegmentEntity> openSegments = session.createQuery(
                            "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                    "WHERE s.gamemodeKey = :gamemodeKey " +
                                    "AND s.endedAt IS NULL AND s.session.endedAt IS NULL",
                            PlayerPlaytimeSegmentEntity.class
                    )
                    .setParameter("gamemodeKey", normalizedGamemodeKey)
                    .list();
            for (PlayerPlaytimeSegmentEntity openSegment : openSegments) {
                accumulateLiveSegment(byPlayer, openSegment, generatedAt);
            }

            return toLeaderboardEntries(byPlayer.values(), resultLimit, generatedAt);
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
            Map<Long, LeaderboardAccumulator> byPlayer = new LinkedHashMap<>();
            List<PlayerPlaytimeEntity> aggregates = session.createQuery(
                            "SELECT t FROM PlayerPlaytimeEntity t",
                            PlayerPlaytimeEntity.class
                    )
                    .list();
            for (PlayerPlaytimeEntity aggregate : aggregates) {
                if (normalizedExcludedGamemodes.contains(aggregate.getGamemodeKey())) {
                    continue;
                }
                accumulateAggregate(byPlayer, aggregate, generatedAt);
            }

            List<PlayerPlaytimeSegmentEntity> openSegments = session.createQuery(
                            "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                    "WHERE s.endedAt IS NULL AND s.session.endedAt IS NULL",
                            PlayerPlaytimeSegmentEntity.class
                    )
                    .list();
            for (PlayerPlaytimeSegmentEntity openSegment : openSegments) {
                if (normalizedExcludedGamemodes.contains(openSegment.getGamemodeKey())) {
                    continue;
                }
                accumulateLiveSegment(byPlayer, openSegment, generatedAt);
            }

            return toLeaderboardEntries(byPlayer.values(), resultLimit, generatedAt);
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

    private static void accumulateAggregate(
            Map<Long, LeaderboardAccumulator> byPlayer,
            PlayerPlaytimeEntity aggregate,
            Instant generatedAt
    ) {
        PlayerEntity player = aggregate.getPlayer();
        if (player == null || player.getId() == null) {
            return;
        }
        LeaderboardAccumulator accumulator = byPlayer.computeIfAbsent(
                player.getId(),
                playerId -> new LeaderboardAccumulator(playerId, player.getUuid(), player.getUsername())
        );
        accumulator.trackedMillis += aggregate.getTrackedMillis();
        accumulator.generatedAt = generatedAt;
    }

    private static void accumulateLiveSegment(
            Map<Long, LeaderboardAccumulator> byPlayer,
            PlayerPlaytimeSegmentEntity segment,
            Instant generatedAt
    ) {
        if (!isLiveSegment(segment, generatedAt)) {
            return;
        }
        PlayerEntity player = segment.getPlayer();
        if (player == null || player.getId() == null) {
            return;
        }
        long liveDeltaMillis = computeLiveDeltaMillis(segment.getLastAccruedAt(), generatedAt);
        if (liveDeltaMillis <= 0L) {
            return;
        }
        LeaderboardAccumulator accumulator = byPlayer.computeIfAbsent(
                player.getId(),
                playerId -> new LeaderboardAccumulator(playerId, player.getUuid(), player.getUsername())
        );
        accumulator.trackedMillis += liveDeltaMillis;
        accumulator.generatedAt = generatedAt;
    }

    private static List<PlayerPlaytimeLeaderboardEntry> toLeaderboardEntries(
            Collection<LeaderboardAccumulator> accumulators,
            int limit,
            Instant generatedAt
    ) {
        List<LeaderboardAccumulator> ranked = accumulators.stream()
                .filter(accumulator -> accumulator.trackedMillis > 0L)
                .sorted(Comparator
                        .comparingLong(LeaderboardAccumulator::trackedMillis).reversed()
                        .thenComparing(LeaderboardAccumulator::username, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(LeaderboardAccumulator::playerId))
                .limit(Math.max(1, limit))
                .toList();
        List<PlayerPlaytimeLeaderboardEntry> entries = new ArrayList<>(ranked.size());
        long rank = 1L;
        for (LeaderboardAccumulator accumulator : ranked) {
            entries.add(new PlayerPlaytimeLeaderboardEntry(
                    rank++,
                    accumulator.playerId,
                    accumulator.playerUuid,
                    accumulator.username,
                    accumulator.trackedMillis,
                    accumulator.generatedAt == null ? generatedAt : accumulator.generatedAt
            ));
        }
        return entries;
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

    private static final class LeaderboardAccumulator {
        private final Long playerId;
        private final String playerUuid;
        private final String username;
        private long trackedMillis;
        private Instant generatedAt;

        private LeaderboardAccumulator(Long playerId, String playerUuid, String username) {
            this.playerId = playerId;
            this.playerUuid = playerUuid;
            this.username = username == null ? "" : username;
        }

        private long trackedMillis() {
            return trackedMillis;
        }

        private String username() {
            return username;
        }

        private Long playerId() {
            return playerId;
        }
    }
}
