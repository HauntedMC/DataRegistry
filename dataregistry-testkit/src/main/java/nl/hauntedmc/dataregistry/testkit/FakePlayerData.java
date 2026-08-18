package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerActivitySnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerConnectionSnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageSettings;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.player.PlayerNameHistoryEntry;
import nl.hauntedmc.dataregistry.api.player.PlayerOnlineSnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerPage;
import nl.hauntedmc.dataregistry.api.player.PlayerPageRequest;
import nl.hauntedmc.dataregistry.api.player.PlayerProfile;
import nl.hauntedmc.dataregistry.api.player.PlayerProfileQuery;
import nl.hauntedmc.dataregistry.api.player.PlayerProfileResult;
import nl.hauntedmc.dataregistry.api.playtime.GamemodePlaytimeStatisticsSnapshot;
import nl.hauntedmc.dataregistry.api.playtime.PlayerGamemodeActivitySnapshot;
import nl.hauntedmc.dataregistry.api.playtime.PlayerGamemodePlaytimeSnapshot;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;
import nl.hauntedmc.dataregistry.api.playtime.TrackedGamemodeSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable in-memory implementation of the complete player-data facade for downstream feature tests.
 * Stored snapshots are returned as-is; time-based reads do not advance them automatically.
 */
public final class FakePlayerData implements PlayerData, PlayerDirectory {

    private static final String CURSOR_PREFIX = "fake-player:";

    private final Set<DataRegistryFeature> enabledFeatures;
    private final Map<Long, PlayerIdentity> identitiesById = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerIdentity> identitiesByUuid = new ConcurrentHashMap<>();
    private final Map<String, PlayerIdentity> identitiesByUsername = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerIdentity> activeIdentities = new ConcurrentHashMap<>();
    private final Map<Long, PlayerLanguageSettings> languages = new ConcurrentHashMap<>();
    private final Map<Long, String> nicknames = new ConcurrentHashMap<>();
    private final Map<Long, PlayerConnectionSnapshot> connections = new ConcurrentHashMap<>();
    private final Map<Long, List<PlayerNameHistoryEntry>> nameHistory = new ConcurrentHashMap<>();
    private final Map<Long, PlayerOnlineSnapshot> onlineStatuses = new ConcurrentHashMap<>();
    private final Map<Long, PlayerActivitySnapshot> activities = new ConcurrentHashMap<>();
    private final Map<Long, PlayerPlaytimeSnapshot> playtime = new ConcurrentHashMap<>();
    private final Map<GamemodeActivityKey, PlayerGamemodeActivitySnapshot> gamemodeActivities = new ConcurrentHashMap<>();
    private final Map<String, GamemodePlaytimeStatisticsSnapshot> gamemodeStatistics = new ConcurrentHashMap<>();
    private final Map<String, TrackedGamemodeSnapshot> trackedGamemodes = new ConcurrentHashMap<>();

    /** Creates a fake with every built-in player domain available. */
    public FakePlayerData() {
        this(EnumSet.allOf(DataRegistryFeature.class));
    }

    /** Creates a fake whose {@link #supports(DataRegistryFeature)} result mirrors the supplied feature set. */
    public FakePlayerData(Set<DataRegistryFeature> enabledFeatures) {
        this.enabledFeatures = Set.copyOf(Objects.requireNonNull(enabledFeatures, "enabledFeatures must not be null"));
    }

    public FakePlayerData putIdentity(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        PlayerIdentity previous = identitiesById.put(identity.playerId(), identity);
        if (previous != null) {
            identitiesByUuid.remove(previous.uuid(), previous);
            identitiesByUsername.remove(normalizeUsername(previous.username()), previous);
        }
        identitiesByUuid.put(identity.uuid(), identity);
        identitiesByUsername.put(normalizeUsername(identity.username()), identity);
        return this;
    }

    public FakePlayerData putActiveIdentity(PlayerIdentity identity) {
        putIdentity(identity);
        activeIdentities.put(identity.uuid(), identity);
        return this;
    }

    public FakePlayerData removeActiveIdentity(UUID uuid) {
        if (uuid != null) {
            activeIdentities.remove(uuid);
        }
        return this;
    }

    public FakePlayerData putLanguage(PlayerLanguageSettings settings) {
        languages.put(settings.playerId(), Objects.requireNonNull(settings, "settings must not be null"));
        return this;
    }

    public FakePlayerData putNickname(long playerId, String nickname) {
        requirePositivePlayerId(playerId);
        nicknames.put(playerId, Objects.requireNonNull(nickname, "nickname must not be null"));
        return this;
    }

    public FakePlayerData putConnection(PlayerConnectionSnapshot connection) {
        connections.put(connection.playerId(), Objects.requireNonNull(connection, "connection must not be null"));
        return this;
    }

    public FakePlayerData putNameHistory(long playerId, Collection<PlayerNameHistoryEntry> entries) {
        requirePositivePlayerId(playerId);
        Objects.requireNonNull(entries, "entries must not be null");
        List<PlayerNameHistoryEntry> copy = entries.stream()
                .map(entry -> Objects.requireNonNull(entry, "name history entry must not be null"))
                .sorted(Comparator.comparing(PlayerNameHistoryEntry::lastSeenAt))
                .toList();
        nameHistory.put(playerId, copy);
        return this;
    }

    public FakePlayerData addNameHistory(PlayerNameHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        nameHistory.compute(entry.playerId(), (ignored, existing) -> {
            List<PlayerNameHistoryEntry> entries = new ArrayList<>(existing == null ? List.of() : existing);
            entries.add(entry);
            entries.sort(Comparator.comparing(PlayerNameHistoryEntry::lastSeenAt));
            return List.copyOf(entries);
        });
        return this;
    }

    public FakePlayerData putOnlineStatus(PlayerOnlineSnapshot status) {
        onlineStatuses.put(status.playerId(), Objects.requireNonNull(status, "status must not be null"));
        return this;
    }

    public FakePlayerData putActivity(PlayerActivitySnapshot activity) {
        activities.put(activity.playerId(), Objects.requireNonNull(activity, "activity must not be null"));
        return this;
    }

    public FakePlayerData putPlaytime(PlayerPlaytimeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (snapshot.playerId() == null || snapshot.playerId() <= 0L) {
            throw new IllegalArgumentException("snapshot playerId must be positive");
        }
        playtime.put(snapshot.playerId(), snapshot);
        for (PlayerGamemodePlaytimeSnapshot gamemode : snapshot.gamemodes()) {
            if (gamemode == null) {
                continue;
            }
            String key = normalizeGamemodeKey(gamemode.gamemodeKey());
            Instant firstObserved = gamemode.firstTrackedAt() == null ? snapshot.generatedAt() : gamemode.firstTrackedAt();
            trackedGamemodes.putIfAbsent(
                    key,
                    new TrackedGamemodeSnapshot(key, gamemode.countedTowardsNetworkTotal(), firstObserved)
            );
        }
        return this;
    }

    public FakePlayerData putGamemodeActivity(PlayerGamemodeActivitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        gamemodeActivities.put(
                new GamemodeActivityKey(snapshot.playerId(), normalizeGamemodeKey(snapshot.gamemodeKey())),
                snapshot
        );
        return this;
    }

    public FakePlayerData putGamemodeStatistics(GamemodePlaytimeStatisticsSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        gamemodeStatistics.put(normalizeGamemodeKey(snapshot.gamemodeKey()), snapshot);
        return this;
    }

    public FakePlayerData putTrackedGamemode(TrackedGamemodeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        trackedGamemodes.put(normalizeGamemodeKey(snapshot.gamemodeKey()), snapshot);
        return this;
    }

    @Override
    public PlayerDirectory identities() {
        return this;
    }

    @Override
    public boolean supports(DataRegistryFeature feature) {
        return feature != null && enabledFeatures.contains(feature);
    }

    @Override
    public Optional<PlayerIdentity> findActiveIdentityCached(UUID uuid) {
        return Optional.ofNullable(uuid == null ? null : activeIdentities.get(uuid));
    }

    @Override
    public Optional<PlayerIdentity> findActiveIdentityCached(String uuid) {
        return parseUuid(uuid).flatMap(this::findActiveIdentityCached);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(PlayerLookup lookup) {
        return completed(resolveIdentity(lookup));
    }

    @Override
    public CompletionStage<Map<PlayerLookup, Optional<PlayerIdentity>>> findIdentities(Collection<PlayerLookup> lookups) {
        if (lookups == null || lookups.isEmpty()) {
            return completed(Map.of());
        }
        Map<PlayerLookup, Optional<PlayerIdentity>> result = new LinkedHashMap<>();
        for (PlayerLookup lookup : lookups) {
            if (lookup != null) {
                result.putIfAbsent(lookup, resolveIdentity(lookup));
            }
        }
        return completed(Map.copyOf(result));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(UUID uuid) {
        return findByUuid(uuid);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(long playerId) {
        return findByPlayerId(playerId);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(String uuid) {
        return findByUuid(uuid);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentityByUsername(String username) {
        return findByUsernameIgnoreCase(username);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentityByIdentifier(String identifier) {
        return findByIdentifier(identifier);
    }

    @Override
    public CompletionStage<List<PlayerIdentity>> findIdentitiesByUsernamePrefix(String prefix, int limit) {
        return findByUsernamePrefix(prefix, limit);
    }

    @Override
    public CompletionStage<PlayerPage<PlayerIdentity>> findIdentitiesByUsernamePrefix(
            String prefix,
            PlayerPageRequest pageRequest
    ) {
        return findByUsernamePrefix(prefix, pageRequest);
    }

    @Override
    public CompletionStage<Optional<Long>> findPlayerId(UUID uuid) {
        return completed(Optional.ofNullable(identitiesByUuid.get(uuid)).map(PlayerIdentity::playerId));
    }

    @Override
    public CompletionStage<Optional<Long>> findPlayerId(String uuid) {
        return completed(parseUuid(uuid).map(identitiesByUuid::get).map(PlayerIdentity::playerId));
    }

    @Override
    public CompletionStage<Optional<Long>> findPlayerIdByIdentifier(String identifier) {
        return completed(resolveIdentifier(identifier).map(PlayerIdentity::playerId));
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        return CompletableFuture.completedFuture(findActiveIdentityCached(uuid));
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(String uuid) {
        return CompletableFuture.completedFuture(findActiveIdentityCached(uuid));
    }

    @Override
    public CompletionStage<Optional<PlayerLanguageSettings>> findLanguage(long playerId) {
        return completed(supports(DataRegistryFeature.LANGUAGE)
                ? Optional.ofNullable(languages.get(playerId)) : Optional.empty());
    }

    @Override
    public CompletionStage<Optional<PlayerLanguageSettings>> findLanguage(UUID uuid) {
        return findPlayerId(uuid).thenApply(playerId -> playerId.flatMap(id -> Optional.ofNullable(languages.get(id))));
    }

    @Override
    public CompletionStage<Void> saveLanguage(long playerId, String language, String effectiveLanguage) {
        requireFeature(DataRegistryFeature.LANGUAGE);
        requirePositivePlayerId(playerId);
        languages.put(playerId, new PlayerLanguageSettings(playerId, language, effectiveLanguage));
        return completed(null);
    }

    @Override
    public CompletionStage<Boolean> saveLanguage(UUID uuid, String language, String effectiveLanguage) {
        Optional<PlayerIdentity> identity = Optional.ofNullable(uuid == null ? null : identitiesByUuid.get(uuid));
        if (identity.isEmpty()) {
            return completed(false);
        }
        saveLanguage(identity.get().playerId(), language, effectiveLanguage);
        return completed(true);
    }

    @Override
    public CompletionStage<Void> clearLanguage(long playerId) {
        languages.remove(playerId);
        return completed(null);
    }

    @Override
    public CompletionStage<Optional<String>> findNickname(long playerId) {
        return completed(supports(DataRegistryFeature.NICKNAMES)
                ? Optional.ofNullable(nicknames.get(playerId)) : Optional.empty());
    }

    @Override
    public CompletionStage<Optional<String>> findNickname(UUID uuid) {
        return findPlayerId(uuid).thenApply(playerId -> playerId.flatMap(id -> Optional.ofNullable(nicknames.get(id))));
    }

    @Override
    public CompletionStage<Void> saveNickname(long playerId, String nickname) {
        requireFeature(DataRegistryFeature.NICKNAMES);
        requirePositivePlayerId(playerId);
        nicknames.put(playerId, Objects.requireNonNull(nickname, "nickname must not be null"));
        return completed(null);
    }

    @Override
    public CompletionStage<Boolean> saveNickname(UUID uuid, String nickname) {
        Optional<PlayerIdentity> identity = Optional.ofNullable(uuid == null ? null : identitiesByUuid.get(uuid));
        if (identity.isEmpty()) {
            return completed(false);
        }
        saveNickname(identity.get().playerId(), nickname);
        return completed(true);
    }

    @Override
    public CompletionStage<Void> clearNickname(long playerId) {
        nicknames.remove(playerId);
        return completed(null);
    }

    @Override
    public CompletionStage<Optional<PlayerConnectionSnapshot>> findConnection(long playerId) {
        return completed(supports(DataRegistryFeature.CONNECTION_INFO)
                ? Optional.ofNullable(connections.get(playerId)) : Optional.empty());
    }

    @Override
    public CompletionStage<Optional<PlayerConnectionSnapshot>> findConnection(UUID uuid) {
        return findPlayerId(uuid).thenApply(playerId -> playerId.flatMap(id -> Optional.ofNullable(connections.get(id))));
    }

    @Override
    public CompletionStage<List<PlayerIdentity>> findIdentitiesByLastIpAddress(String ipAddress, Long excludePlayerId) {
        if (!supports(DataRegistryFeature.CONNECTION_INFO) || ipAddress == null || ipAddress.isBlank()) {
            return completed(List.of());
        }
        return completed(connections.values().stream()
                .filter(connection -> ipAddress.equals(connection.ipAddress()))
                .filter(connection -> excludePlayerId == null || connection.playerId() != excludePlayerId)
                .map(connection -> identitiesById.get(connection.playerId()))
                .filter(Objects::nonNull)
                .sorted(identityComparator())
                .toList());
    }

    @Override
    public CompletionStage<List<Long>> findPlayerIdsByLastIpAddress(String ipAddress, Long excludePlayerId) {
        return findIdentitiesByLastIpAddress(ipAddress, excludePlayerId)
                .thenApply(identities -> identities.stream().map(PlayerIdentity::playerId).toList());
    }

    @Override
    public CompletionStage<List<String>> findUsernamesByLastIpAddress(String ipAddress, Long excludePlayerId) {
        return findIdentitiesByLastIpAddress(ipAddress, excludePlayerId)
                .thenApply(identities -> identities.stream().map(PlayerIdentity::username).toList());
    }

    @Override
    public CompletionStage<List<PlayerIdentity>> findIdentitiesSharingLastIp(long playerId) {
        PlayerConnectionSnapshot connection = connections.get(playerId);
        return connection == null || connection.ipAddress() == null
                ? completed(List.of())
                : findIdentitiesByLastIpAddress(connection.ipAddress(), playerId);
    }

    @Override
    public CompletionStage<List<String>> findUsernamesSharingLastIp(long playerId) {
        return findIdentitiesSharingLastIp(playerId)
                .thenApply(identities -> identities.stream().map(PlayerIdentity::username).toList());
    }

    @Override
    public CompletionStage<List<PlayerNameHistoryEntry>> findNameHistory(long playerId, int limit) {
        if (!supports(DataRegistryFeature.NAME_HISTORY) || playerId <= 0L) {
            return completed(List.of());
        }
        return completed(limit(nameHistory.getOrDefault(playerId, List.of()), limit));
    }

    @Override
    public CompletionStage<List<PlayerNameHistoryEntry>> findNameHistory(UUID uuid, int limit) {
        return findPlayerId(uuid).thenCompose(playerId -> playerId
                .map(id -> findNameHistory(id, limit))
                .orElseGet(() -> completed(List.of())));
    }

    @Override
    public CompletionStage<List<PlayerNameHistoryEntry>> findNameHistoryByCurrentUsername(String username, int limit) {
        Optional<PlayerIdentity> identity = resolveUsername(username);
        return identity.isEmpty() ? completed(List.of()) : findNameHistory(identity.get().playerId(), limit);
    }

    @Override
    public CompletionStage<Optional<PlayerOnlineSnapshot>> findOnlineStatus(long playerId) {
        return completed(supports(DataRegistryFeature.ONLINE_STATUS)
                ? Optional.ofNullable(onlineStatuses.get(playerId)) : Optional.empty());
    }

    @Override
    public CompletionStage<List<PlayerOnlineSnapshot>> findOnlinePlayers(int limit) {
        if (!supports(DataRegistryFeature.ONLINE_STATUS)) {
            return completed(List.of());
        }
        return completed(limit(onlineStatuses.values().stream()
                .filter(PlayerOnlineSnapshot::online)
                .sorted(Comparator.comparingLong(PlayerOnlineSnapshot::playerId))
                .toList(), limit));
    }

    @Override
    public CompletionStage<List<PlayerOnlineSnapshot>> findOnlinePlayersByServer(String serverName, int limit) {
        if (!supports(DataRegistryFeature.ONLINE_STATUS) || serverName == null || serverName.isBlank()) {
            return completed(List.of());
        }
        return completed(limit(onlineStatuses.values().stream()
                .filter(PlayerOnlineSnapshot::online)
                .filter(status -> status.currentServer() != null && status.currentServer().equalsIgnoreCase(serverName.trim()))
                .sorted(Comparator.comparingLong(PlayerOnlineSnapshot::playerId))
                .toList(), limit));
    }

    @Override
    public CompletionStage<Optional<PlayerActivitySnapshot>> findActivity(long playerId) {
        return completed(supports(DataRegistryFeature.ACTIVITY_SUMMARY)
                ? Optional.ofNullable(activities.get(playerId)) : Optional.empty());
    }

    @Override
    public CompletionStage<List<PlayerActivitySnapshot>> findRecentlySeen(int limit) {
        if (!supports(DataRegistryFeature.ACTIVITY_SUMMARY)) {
            return completed(List.of());
        }
        return completed(limit(activities.values().stream()
                .sorted(Comparator.comparing(PlayerActivitySnapshot::lastSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList(), limit));
    }

    @Override
    public CompletionStage<Optional<PlayerPlaytimeSnapshot>> findPlaytime(long playerId) {
        return completed(supports(DataRegistryFeature.PLAYTIME)
                ? Optional.ofNullable(playtime.get(playerId)) : Optional.empty());
    }

    @Override
    public CompletionStage<Optional<PlayerPlaytimeSnapshot>> findPlaytime(long playerId, Instant asOf) {
        return findPlaytime(playerId);
    }

    @Override
    public CompletionStage<Optional<PlayerGamemodeActivitySnapshot>> findGamemodeActivity(
            PlayerLookup lookup,
            String gamemodeKey
    ) {
        String normalizedKey = normalizeGamemodeKey(gamemodeKey);
        return findIdentity(lookup).thenApply(identity -> identity.flatMap(value -> Optional.ofNullable(
                gamemodeActivities.get(new GamemodeActivityKey(value.playerId(), normalizedKey))
        )));
    }

    @Override
    public CompletionStage<Optional<PlayerGamemodeActivitySnapshot>> findGamemodeActivity(
            long playerId,
            String gamemodeKey
    ) {
        return completed(supports(DataRegistryFeature.PLAYTIME)
                ? Optional.ofNullable(gamemodeActivities.get(
                        new GamemodeActivityKey(playerId, normalizeGamemodeKey(gamemodeKey))))
                : Optional.empty());
    }

    @Override
    public CompletionStage<Optional<GamemodePlaytimeStatisticsSnapshot>> findGamemodeStatistics(String gamemodeKey) {
        return completed(supports(DataRegistryFeature.PLAYTIME)
                ? Optional.ofNullable(gamemodeStatistics.get(normalizeGamemodeKey(gamemodeKey)))
                : Optional.empty());
    }

    @Override
    public CompletionStage<List<PlayerPlaytimeLeaderboardEntry>> findTopPlaytime(int limit) {
        if (!supports(DataRegistryFeature.PLAYTIME)) {
            return completed(List.of());
        }
        List<PlayerPlaytimeSnapshot> ranked = playtime.values().stream()
                .sorted(Comparator.comparingLong(PlayerPlaytimeSnapshot::networkTotalMillis).reversed()
                        .thenComparing(snapshot -> snapshot.playerId() == null ? Long.MAX_VALUE : snapshot.playerId()))
                .limit(normalizeLimit(limit))
                .toList();
        return completed(toLeaderboard(ranked, null));
    }

    @Override
    public CompletionStage<List<PlayerPlaytimeLeaderboardEntry>> findTopPlaytimeByGamemode(String gamemodeKey, int limit) {
        if (!supports(DataRegistryFeature.PLAYTIME)) {
            return completed(List.of());
        }
        String normalizedKey = normalizeGamemodeKey(gamemodeKey);
        List<PlayerPlaytimeSnapshot> ranked = playtime.values().stream()
                .filter(snapshot -> gamemodeMillis(snapshot, normalizedKey) > 0L)
                .sorted(Comparator.comparingLong((PlayerPlaytimeSnapshot snapshot) -> gamemodeMillis(snapshot, normalizedKey))
                        .reversed()
                        .thenComparing(snapshot -> snapshot.playerId() == null ? Long.MAX_VALUE : snapshot.playerId()))
                .limit(normalizeLimit(limit))
                .toList();
        return completed(toLeaderboard(ranked, normalizedKey));
    }

    @Override
    public CompletionStage<List<String>> findTrackedGamemodeKeys() {
        if (!supports(DataRegistryFeature.PLAYTIME)) {
            return completed(List.of());
        }
        return completed(trackedGamemodes.keySet().stream().sorted().toList());
    }

    @Override
    public CompletionStage<List<TrackedGamemodeSnapshot>> findTrackedGamemodes() {
        if (!supports(DataRegistryFeature.PLAYTIME)) {
            return completed(List.of());
        }
        return completed(trackedGamemodes.values().stream()
                .sorted(Comparator.comparing(TrackedGamemodeSnapshot::gamemodeKey))
                .toList());
    }

    @Override
    public CompletionStage<PlayerProfileResult> findProfile(PlayerLookup lookup, PlayerProfileQuery query) {
        Objects.requireNonNull(lookup, "lookup must not be null");
        PlayerProfileQuery resolvedQuery = query == null ? PlayerProfileQuery.defaults() : query;
        return completed(new PlayerProfileResult(lookup, resolvedQuery, resolveIdentity(lookup)
                .map(identity -> profile(identity, resolvedQuery.nameHistoryLimit()))));
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfile(PlayerIdentity identity, int nameHistoryLimit) {
        return completed(identity == null ? Optional.empty() : Optional.of(profile(identity, nameHistoryLimit)));
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfile(long playerId, int nameHistoryLimit) {
        return completed(Optional.ofNullable(identitiesById.get(playerId)).map(identity -> profile(identity, nameHistoryLimit)));
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfile(UUID uuid, int nameHistoryLimit) {
        return completed(Optional.ofNullable(uuid == null ? null : identitiesByUuid.get(uuid))
                .map(identity -> profile(identity, nameHistoryLimit)));
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfileByUsername(String username, int nameHistoryLimit) {
        return completed(resolveUsername(username).map(identity -> profile(identity, nameHistoryLimit)));
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfileByIdentifier(String identifier, int nameHistoryLimit) {
        return completed(resolveIdentifier(identifier).map(identity -> profile(identity, nameHistoryLimit)));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByUuid(UUID uuid) {
        return completed(Optional.ofNullable(uuid == null ? null : identitiesByUuid.get(uuid)));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByPlayerId(long playerId) {
        return completed(Optional.ofNullable(identitiesById.get(playerId)));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByUuid(String uuid) {
        return completed(parseUuid(uuid).map(identitiesByUuid::get));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByUsername(String username) {
        return completed(resolveUsername(username));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByUsernameIgnoreCase(String username) {
        return completed(resolveUsername(username));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByIdentifier(String identifier) {
        return completed(resolveIdentifier(identifier));
    }

    @Override
    public CompletionStage<List<PlayerIdentity>> findByUsernamePrefix(String prefix, int limit) {
        return completed(limit(prefixMatches(prefix), limit));
    }

    @Override
    public CompletionStage<PlayerPage<PlayerIdentity>> findByUsernamePrefix(String prefix, PlayerPageRequest pageRequest) {
        PlayerPageRequest request = pageRequest == null
                ? PlayerPageRequest.firstPage(PlayerPageRequest.DEFAULT_LIMIT) : pageRequest;
        List<PlayerIdentity> matches = prefixMatches(prefix);
        int start = cursorStart(matches, request.afterCursor());
        int end = Math.min(matches.size(), start + request.limit());
        List<PlayerIdentity> items = matches.subList(start, end);
        Optional<String> nextCursor = end < matches.size() && !items.isEmpty()
                ? Optional.of(CURSOR_PREFIX + items.getLast().playerId()) : Optional.empty();
        return completed(new PlayerPage<>(items, nextCursor));
    }

    @Override
    public Map<String, PlayerIdentity> snapshotActiveIdentities() {
        Map<String, PlayerIdentity> snapshot = new LinkedHashMap<>();
        activeIdentities.values().stream()
                .sorted(identityComparator())
                .forEach(identity -> snapshot.put(identity.uuid().toString(), identity));
        return Map.copyOf(snapshot);
    }

    private PlayerProfile profile(PlayerIdentity identity, int nameHistoryLimit) {
        long playerId = identity.playerId();
        return new PlayerProfile(
                identity,
                supports(DataRegistryFeature.LANGUAGE) ? Optional.ofNullable(languages.get(playerId)) : Optional.empty(),
                supports(DataRegistryFeature.NICKNAMES) ? Optional.ofNullable(nicknames.get(playerId)) : Optional.empty(),
                supports(DataRegistryFeature.CONNECTION_INFO)
                        ? Optional.ofNullable(connections.get(playerId)) : Optional.empty(),
                supports(DataRegistryFeature.ONLINE_STATUS)
                        ? Optional.ofNullable(onlineStatuses.get(playerId)) : Optional.empty(),
                supports(DataRegistryFeature.ACTIVITY_SUMMARY)
                        ? Optional.ofNullable(activities.get(playerId)) : Optional.empty(),
                supports(DataRegistryFeature.PLAYTIME) ? Optional.ofNullable(playtime.get(playerId)) : Optional.empty(),
                supports(DataRegistryFeature.NAME_HISTORY)
                        ? limit(nameHistory.getOrDefault(playerId, List.of()), nameHistoryLimit) : List.of()
        );
    }

    private Optional<PlayerIdentity> resolveIdentity(PlayerLookup lookup) {
        if (lookup == null) {
            return Optional.empty();
        }
        return switch (lookup.type()) {
            case PLAYER_ID -> Optional.ofNullable(identitiesById.get(lookup.playerId()));
            case UUID -> Optional.ofNullable(identitiesByUuid.get(lookup.uuid()));
            case USERNAME -> resolveUsername(lookup.text());
            case IDENTIFIER -> resolveIdentifier(lookup.text());
        };
    }

    private Optional<PlayerIdentity> resolveIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        Optional<UUID> uuid = parseUuid(identifier);
        return uuid.isPresent() ? Optional.ofNullable(identitiesByUuid.get(uuid.get())) : resolveUsername(identifier);
    }

    private Optional<PlayerIdentity> resolveUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(identitiesByUsername.get(normalizeUsername(username)));
    }

    private List<PlayerIdentity> prefixMatches(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        String normalizedPrefix = normalizeUsername(prefix);
        return identitiesById.values().stream()
                .filter(identity -> normalizeUsername(identity.username()).startsWith(normalizedPrefix))
                .sorted(identityComparator())
                .toList();
    }

    private static Comparator<PlayerIdentity> identityComparator() {
        return Comparator.comparing((PlayerIdentity identity) -> identity.username().toLowerCase(Locale.ROOT))
                .thenComparingLong(PlayerIdentity::playerId);
    }

    private static int cursorStart(List<PlayerIdentity> matches, String cursor) {
        if (cursor == null || !cursor.startsWith(CURSOR_PREFIX)) {
            return 0;
        }
        try {
            long playerId = Long.parseLong(cursor.substring(CURSOR_PREFIX.length()));
            for (int index = 0; index < matches.size(); index++) {
                if (matches.get(index).playerId() == playerId) {
                    return index + 1;
                }
            }
        } catch (NumberFormatException ignored) {
            // Opaque cursors from another implementation intentionally restart this fake at page one.
        }
        return 0;
    }

    private static List<PlayerPlaytimeLeaderboardEntry> toLeaderboard(
            List<PlayerPlaytimeSnapshot> ranked,
            String gamemodeKey
    ) {
        List<PlayerPlaytimeLeaderboardEntry> result = new ArrayList<>(ranked.size());
        long rank = 1L;
        for (PlayerPlaytimeSnapshot snapshot : ranked) {
            long millis = gamemodeKey == null ? snapshot.networkTotalMillis() : gamemodeMillis(snapshot, gamemodeKey);
            result.add(new PlayerPlaytimeLeaderboardEntry(
                    rank++,
                    snapshot.playerId(),
                    snapshot.playerUuid(),
                    snapshot.username(),
                    millis,
                    snapshot.generatedAt()
            ));
        }
        return List.copyOf(result);
    }

    private static long gamemodeMillis(PlayerPlaytimeSnapshot snapshot, String gamemodeKey) {
        return snapshot.gamemodes().stream()
                .filter(gamemode -> gamemodeKey.equalsIgnoreCase(gamemode.gamemodeKey()))
                .mapToLong(PlayerGamemodePlaytimeSnapshot::trackedMillis)
                .findFirst()
                .orElse(0L);
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeGamemodeKey(String gamemodeKey) {
        Objects.requireNonNull(gamemodeKey, "gamemodeKey must not be null");
        String normalized = gamemodeKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 64 || !normalized.matches("[a-z0-9._:-]+")) {
            throw new IllegalArgumentException("Invalid gamemode key: " + gamemodeKey);
        }
        return normalized;
    }

    private static Optional<UUID> parseUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(uuid.trim()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static int normalizeLimit(int limit) {
        return Math.max(1, limit);
    }

    private static <T> List<T> limit(List<T> values, int limit) {
        return values.subList(0, Math.min(values.size(), normalizeLimit(limit)));
    }

    private void requireFeature(DataRegistryFeature feature) {
        if (!supports(feature)) {
            throw new IllegalStateException("DataRegistry feature is disabled in FakePlayerData: " + feature.configKey());
        }
    }

    private static void requirePositivePlayerId(long playerId) {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be positive");
        }
    }

    private static <T> CompletableFuture<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private record GamemodeActivityKey(long playerId, String gamemodeKey) {
    }
}
