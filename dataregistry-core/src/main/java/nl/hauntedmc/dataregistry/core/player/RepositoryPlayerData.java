package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerLanguageEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerNicknameEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeSegmentEntity;
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
import nl.hauntedmc.dataregistry.api.playtime.PlayerGamemodePlaytimeSnapshot;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerActivitySummaryRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerConnectionInfoRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerLanguageRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerNameHistoryRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerNicknameRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerOnlineStatusRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerPlaytimeRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerRepository;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;

import java.time.Duration;
import java.time.Instant;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Repository-backed implementation of the public player-data facade.
 */
public final class RepositoryPlayerData implements PlayerData {

    private final PlayerDirectory playerDirectory;
    private final DataRegistryQueryExecutor queryExecutor;
    private final ORMContext ormContext;
    private final Set<DataRegistryFeature> enabledFeatures;
    private final PlayerActivitySummaryRepository activitySummaryRepository;
    private final PlayerOnlineStatusRepository onlineStatusRepository;
    private final PlayerConnectionInfoRepository connectionInfoRepository;
    private final PlayerLanguageRepository languageRepository;
    private final PlayerNicknameRepository nicknameRepository;
    private final PlayerNameHistoryRepository nameHistoryRepository;
    private final PlayerPlaytimeRepository playtimeRepository;
    private final Set<String> playtimeExcludedGamemodeKeys;

    public RepositoryPlayerData(
            PlayerDirectory playerDirectory,
            Set<DataRegistryFeature> enabledFeatures,
            PlayerActivitySummaryRepository activitySummaryRepository,
            PlayerOnlineStatusRepository onlineStatusRepository,
            PlayerConnectionInfoRepository connectionInfoRepository,
            PlayerLanguageRepository languageRepository,
            PlayerNicknameRepository nicknameRepository,
            PlayerNameHistoryRepository nameHistoryRepository,
            PlayerPlaytimeRepository playtimeRepository
    ) {
        this(
                playerDirectory,
                DataRegistryQueryExecutor.immediateForTesting(),
                null,
                enabledFeatures,
                activitySummaryRepository,
                onlineStatusRepository,
                connectionInfoRepository,
                languageRepository,
                nicknameRepository,
                nameHistoryRepository,
                playtimeRepository,
                Set.of()
        );
    }

    public RepositoryPlayerData(
            PlayerDirectory playerDirectory,
            DataRegistryQueryExecutor queryExecutor,
            ORMContext ormContext,
            Set<DataRegistryFeature> enabledFeatures,
            PlayerActivitySummaryRepository activitySummaryRepository,
            PlayerOnlineStatusRepository onlineStatusRepository,
            PlayerConnectionInfoRepository connectionInfoRepository,
            PlayerLanguageRepository languageRepository,
            PlayerNicknameRepository nicknameRepository,
            PlayerNameHistoryRepository nameHistoryRepository,
            PlayerPlaytimeRepository playtimeRepository,
            Collection<String> playtimeExcludedGamemodeKeys
    ) {
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory must not be null");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor must not be null");
        this.ormContext = ormContext;
        this.enabledFeatures = Set.copyOf(Objects.requireNonNull(enabledFeatures, "enabledFeatures must not be null"));
        this.activitySummaryRepository = activitySummaryRepository;
        this.onlineStatusRepository = onlineStatusRepository;
        this.connectionInfoRepository = connectionInfoRepository;
        this.languageRepository = languageRepository;
        this.nicknameRepository = nicknameRepository;
        this.nameHistoryRepository = nameHistoryRepository;
        this.playtimeRepository = playtimeRepository;
        this.playtimeExcludedGamemodeKeys = normalizeGamemodeKeys(playtimeExcludedGamemodeKeys);
    }

    @Override
    public PlayerDirectory identities() {
        return playerDirectory;
    }

    @Override
    public boolean supports(DataRegistryFeature feature) {
        return enabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    @Override
    public Optional<PlayerIdentity> findActiveIdentityCached(UUID uuid) {
        return playerDirectory.findActiveIdentityCached(uuid);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(PlayerLookup lookup) {
        return playerDirectory.findIdentity(lookup);
    }

    @Override
    public CompletionStage<Map<PlayerLookup, Optional<PlayerIdentity>>> findIdentities(Collection<PlayerLookup> lookups) {
        return playerDirectory.findIdentities(lookups);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(UUID uuid) {
        return playerDirectory.findByUuid(uuid);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(long playerId) {
        return playerDirectory.findByPlayerId(playerId);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(String uuid) {
        return playerDirectory.findByUuid(uuid);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentityByUsername(String username) {
        return playerDirectory.findByUsernameIgnoreCase(username);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentityByIdentifier(String identifier) {
        return playerDirectory.findByIdentifier(identifier);
    }

    @Override
    public CompletionStage<List<PlayerIdentity>> findIdentitiesByUsernamePrefix(String prefix, int limit) {
        return playerDirectory.findByUsernamePrefix(prefix, limit);
    }

    @Override
    public CompletionStage<PlayerPage<PlayerIdentity>> findIdentitiesByUsernamePrefix(
            String prefix,
            PlayerPageRequest pageRequest
    ) {
        return playerDirectory.findByUsernamePrefix(prefix, pageRequest);
    }

    @Override
    public CompletionStage<Optional<Long>> findPlayerId(UUID uuid) {
        Optional<PlayerIdentity> cached = findActiveIdentityCached(uuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.map(PlayerIdentity::playerId));
        }
        return findIdentity(uuid).thenApply(identity -> identity.map(PlayerIdentity::playerId));
    }

    @Override
    public CompletionStage<Optional<Long>> findPlayerId(String uuid) {
        Optional<PlayerIdentity> cached = playerDirectory.findActiveIdentityCached(uuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.map(PlayerIdentity::playerId));
        }
        return findIdentity(uuid).thenApply(identity -> identity.map(PlayerIdentity::playerId));
    }

    @Override
    public CompletionStage<Optional<Long>> findPlayerIdByIdentifier(String identifier) {
        return findIdentityByIdentifier(identifier).thenApply(identity -> identity.map(PlayerIdentity::playerId));
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        return playerDirectory.whenReady(uuid);
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(String uuid) {
        return playerDirectory.whenReady(uuid);
    }

    @Override
    public CompletionStage<Optional<PlayerLanguageSettings>> findLanguage(long playerId) {
        return queryExecutor.supply("player.language.find", () -> findLanguageSync(playerId));
    }

    @Override
    public CompletionStage<Optional<PlayerLanguageSettings>> findLanguage(UUID uuid) {
        return findPlayerId(uuid).thenCompose(playerId -> playerId
                .map(this::findLanguage)
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    @Override
    public CompletionStage<Void> saveLanguage(long playerId, String language, String effectiveLanguage) {
        return queryExecutor.supply("player.language.save", () -> {
            requireRepository(languageRepository, DataRegistryFeature.LANGUAGE);
            if (playerId <= 0L) {
                throw new IllegalArgumentException("playerId must be a positive database id.");
            }
            languageRepository.saveOrUpdate(playerId, language, effectiveLanguage);
            return null;
        });
    }

    @Override
    public CompletionStage<Boolean> saveLanguage(UUID uuid, String language, String effectiveLanguage) {
        return findPlayerId(uuid).thenCompose(playerId -> {
            if (playerId.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return saveLanguage(playerId.get(), language, effectiveLanguage).thenApply(ignored -> true);
        });
    }

    @Override
    public CompletionStage<Void> clearLanguage(long playerId) {
        return queryExecutor.supply("player.language.clear", () -> {
            if (languageRepository != null && playerId > 0L) {
                languageRepository.deleteByPlayerId(playerId);
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Optional<String>> findNickname(long playerId) {
        return queryExecutor.supply("player.nickname.find", () -> findNicknameSync(playerId));
    }

    @Override
    public CompletionStage<Optional<String>> findNickname(UUID uuid) {
        return findPlayerId(uuid).thenCompose(playerId -> playerId
                .map(this::findNickname)
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    @Override
    public CompletionStage<Void> saveNickname(long playerId, String nickname) {
        return queryExecutor.supply("player.nickname.save", () -> {
            requireRepository(nicknameRepository, DataRegistryFeature.NICKNAMES);
            if (playerId <= 0L) {
                throw new IllegalArgumentException("playerId must be a positive database id.");
            }
            nicknameRepository.saveOrUpdate(playerId, nickname);
            return null;
        });
    }

    @Override
    public CompletionStage<Boolean> saveNickname(UUID uuid, String nickname) {
        return findPlayerId(uuid).thenCompose(playerId -> {
            if (playerId.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return saveNickname(playerId.get(), nickname).thenApply(ignored -> true);
        });
    }

    @Override
    public CompletionStage<Void> clearNickname(long playerId) {
        return queryExecutor.supply("player.nickname.clear", () -> {
            if (nicknameRepository != null && playerId > 0L) {
                nicknameRepository.deleteByPlayerId(playerId);
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Optional<PlayerConnectionSnapshot>> findConnection(long playerId) {
        return queryExecutor.supply("player.connection.find", () -> findConnectionSync(playerId));
    }

    @Override
    public CompletionStage<Optional<PlayerConnectionSnapshot>> findConnection(UUID uuid) {
        return findPlayerId(uuid).thenCompose(playerId -> playerId
                .map(this::findConnection)
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    @Override
    public CompletionStage<List<PlayerIdentity>> findIdentitiesByLastIpAddress(String ipAddress, Long excludePlayerId) {
        return queryExecutor.supply("player.connection.ip-identities", () -> {
            if (connectionInfoRepository == null) {
                return List.of();
            }
            return connectionInfoRepository.findIdentitiesByLastIpAddress(ipAddress, excludePlayerId);
        });
    }

    @Override
    public CompletionStage<List<Long>> findPlayerIdsByLastIpAddress(String ipAddress, Long excludePlayerId) {
        return queryExecutor.supply("player.connection.ip-ids", () -> {
            if (connectionInfoRepository == null) {
                return List.of();
            }
            return connectionInfoRepository.findPlayerIdsByLastIpAddress(ipAddress, excludePlayerId);
        });
    }

    @Override
    public CompletionStage<List<String>> findUsernamesByLastIpAddress(String ipAddress, Long excludePlayerId) {
        return queryExecutor.supply("player.connection.ip-usernames", () -> {
            if (connectionInfoRepository == null) {
                return List.of();
            }
            return connectionInfoRepository.findUsernamesByLastIpAddress(ipAddress, excludePlayerId);
        });
    }

    @Override
    public CompletionStage<List<PlayerIdentity>> findIdentitiesSharingLastIp(long playerId) {
        return findConnection(playerId).thenCompose(connection -> connection
                .map(PlayerConnectionSnapshot::ipAddress)
                .filter(ipAddress -> !ipAddress.isBlank())
                .map(ipAddress -> findIdentitiesByLastIpAddress(ipAddress, playerId))
                .orElseGet(() -> CompletableFuture.completedFuture(List.of())));
    }

    @Override
    public CompletionStage<List<String>> findUsernamesSharingLastIp(long playerId) {
        return findConnection(playerId).thenCompose(connection -> connection
                .map(PlayerConnectionSnapshot::ipAddress)
                .filter(ipAddress -> !ipAddress.isBlank())
                .map(ipAddress -> findUsernamesByLastIpAddress(ipAddress, playerId))
                .orElseGet(() -> CompletableFuture.completedFuture(List.of())));
    }

    @Override
    public CompletionStage<List<PlayerNameHistoryEntry>> findNameHistory(long playerId, int limit) {
        return queryExecutor.supply("player.name-history.find", () -> findNameHistorySync(playerId, limit));
    }

    @Override
    public CompletionStage<List<PlayerNameHistoryEntry>> findNameHistory(UUID uuid, int limit) {
        return findPlayerId(uuid).thenCompose(playerId -> playerId
                .map(value -> findNameHistory(value, limit))
                .orElseGet(() -> CompletableFuture.completedFuture(List.of())));
    }

    @Override
    public CompletionStage<List<PlayerNameHistoryEntry>> findNameHistoryByCurrentUsername(String username, int limit) {
        return findIdentityByUsername(username).thenCompose(identity -> identity
                .map(PlayerIdentity::playerId)
                .map(playerId -> findNameHistory(playerId, limit))
                .orElseGet(() -> CompletableFuture.completedFuture(List.of())));
    }

    @Override
    public CompletionStage<Optional<PlayerOnlineSnapshot>> findOnlineStatus(long playerId) {
        return queryExecutor.supply("player.online.find", () -> findOnlineStatusSync(playerId));
    }

    @Override
    public CompletionStage<List<PlayerOnlineSnapshot>> findOnlinePlayers(int limit) {
        return queryExecutor.supply("player.online.list", () -> {
            if (onlineStatusRepository == null) {
                return List.of();
            }
            return onlineStatusRepository.findOnlinePlayers(limit)
                    .stream()
                    .map(RepositoryPlayerData::toOnlineSnapshot)
                    .toList();
        });
    }

    @Override
    public CompletionStage<List<PlayerOnlineSnapshot>> findOnlinePlayersByServer(String serverName, int limit) {
        return queryExecutor.supply("player.online.server-list", () -> {
            if (onlineStatusRepository == null) {
                return List.of();
            }
            return onlineStatusRepository.findOnlinePlayersByServer(serverName, limit)
                    .stream()
                    .map(RepositoryPlayerData::toOnlineSnapshot)
                    .toList();
        });
    }

    @Override
    public CompletionStage<Optional<PlayerActivitySnapshot>> findActivity(long playerId) {
        return queryExecutor.supply("player.activity.find", () -> findActivitySync(playerId));
    }

    @Override
    public CompletionStage<List<PlayerActivitySnapshot>> findRecentlySeen(int limit) {
        return queryExecutor.supply("player.activity.recent", () -> {
            if (activitySummaryRepository == null) {
                return List.of();
            }
            return activitySummaryRepository.findRecentlySeen(limit)
                    .stream()
                    .map(RepositoryPlayerData::toActivitySnapshot)
                    .toList();
        });
    }

    @Override
    public CompletionStage<Optional<PlayerPlaytimeSnapshot>> findPlaytime(long playerId) {
        return queryExecutor.supply("player.playtime.find", () -> findPlaytimeSync(playerId, Instant.now()));
    }

    @Override
    public CompletionStage<Optional<PlayerPlaytimeSnapshot>> findPlaytime(long playerId, Instant asOf) {
        return queryExecutor.supply(
                "player.playtime.find-at",
                () -> findPlaytimeSync(playerId, Objects.requireNonNullElseGet(asOf, Instant::now))
        );
    }

    @Override
    public CompletionStage<List<PlayerPlaytimeLeaderboardEntry>> findTopPlaytime(int limit) {
        return queryExecutor.supply("player.playtime.leaderboard", () -> {
            if (playtimeRepository == null) {
                return List.of();
            }
            return playtimeRepository.findTopPlayersByNetworkTotal(limit);
        });
    }

    @Override
    public CompletionStage<List<PlayerPlaytimeLeaderboardEntry>> findTopPlaytimeByGamemode(String gamemodeKey, int limit) {
        return queryExecutor.supply("player.playtime.gamemode-leaderboard", () -> {
            if (playtimeRepository == null) {
                return List.of();
            }
            return playtimeRepository.findTopPlayersByGamemode(gamemodeKey, limit);
        });
    }

    @Override
    public CompletionStage<List<String>> findTrackedGamemodeKeys() {
        return queryExecutor.supply("player.playtime.gamemode-keys", () -> {
            if (playtimeRepository == null) {
                return List.of();
            }
            return playtimeRepository.findTrackedGamemodeKeys();
        });
    }

    @Override
    public CompletionStage<PlayerProfileResult> findProfile(PlayerLookup lookup, PlayerProfileQuery query) {
        Objects.requireNonNull(lookup, "lookup must not be null");
        PlayerProfileQuery profileQuery = query == null ? PlayerProfileQuery.defaults() : query;
        return queryExecutor.supply(
                "player.profile.find",
                () -> new PlayerProfileResult(lookup, profileQuery, findProfileProjection(lookup, profileQuery))
        );
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfile(PlayerIdentity identity, int nameHistoryLimit) {
        if (identity == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        PlayerProfileQuery query = PlayerProfileQuery.withNameHistoryLimit(nameHistoryLimit);
        return queryExecutor.supply(
                "player.profile.identity",
                () -> findProfileProjection(identity, query)
        );
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfile(long playerId, int nameHistoryLimit) {
        if (playerId <= 0L) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return findProfile(PlayerLookup.playerId(playerId), PlayerProfileQuery.withNameHistoryLimit(nameHistoryLimit))
                .thenApply(PlayerProfileResult::profile);
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfile(UUID uuid, int nameHistoryLimit) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return findProfile(PlayerLookup.uuid(uuid), PlayerProfileQuery.withNameHistoryLimit(nameHistoryLimit))
                .thenApply(PlayerProfileResult::profile);
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfileByUsername(String username, int nameHistoryLimit) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return findProfile(PlayerLookup.username(username), PlayerProfileQuery.withNameHistoryLimit(nameHistoryLimit))
                .thenApply(PlayerProfileResult::profile);
    }

    @Override
    public CompletionStage<Optional<PlayerProfile>> findProfileByIdentifier(String identifier, int nameHistoryLimit) {
        if (identifier == null || identifier.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return findProfile(PlayerLookup.identifier(identifier), PlayerProfileQuery.withNameHistoryLimit(nameHistoryLimit))
                .thenApply(PlayerProfileResult::profile);
    }

    private Optional<PlayerLanguageSettings> findLanguageSync(long playerId) {
        if (playerId <= 0L || languageRepository == null) {
            return Optional.empty();
        }
        return languageRepository.findByPlayerId(playerId).map(RepositoryPlayerData::toLanguageSettings);
    }

    private Optional<String> findNicknameSync(long playerId) {
        if (playerId <= 0L || nicknameRepository == null) {
            return Optional.empty();
        }
        return nicknameRepository.findNicknameByPlayerId(playerId);
    }

    private Optional<PlayerConnectionSnapshot> findConnectionSync(long playerId) {
        if (playerId <= 0L || connectionInfoRepository == null) {
            return Optional.empty();
        }
        return connectionInfoRepository.findByPlayerId(playerId).map(RepositoryPlayerData::toConnectionSnapshot);
    }

    private List<PlayerNameHistoryEntry> findNameHistorySync(long playerId, int limit) {
        if (playerId <= 0L || nameHistoryRepository == null) {
            return List.of();
        }
        return nameHistoryRepository.findChronologicalByPlayer(playerId, limit)
                .stream()
                .map(RepositoryPlayerData::toNameHistoryEntry)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<PlayerOnlineSnapshot> findOnlineStatusSync(long playerId) {
        if (playerId <= 0L || onlineStatusRepository == null) {
            return Optional.empty();
        }
        return onlineStatusRepository.findByPlayerId(playerId).map(RepositoryPlayerData::toOnlineSnapshot);
    }

    private Optional<PlayerActivitySnapshot> findActivitySync(long playerId) {
        if (playerId <= 0L || activitySummaryRepository == null) {
            return Optional.empty();
        }
        return activitySummaryRepository.findByPlayerId(playerId).map(RepositoryPlayerData::toActivitySnapshot);
    }

    private Optional<PlayerPlaytimeSnapshot> findPlaytimeSync(long playerId, Instant asOf) {
        if (playerId <= 0L || playtimeRepository == null) {
            return Optional.empty();
        }
        return playtimeRepository.findSnapshotByPlayerId(playerId, asOf);
    }

    private Optional<PlayerProfile> findProfileProjection(PlayerLookup lookup, PlayerProfileQuery query) {
        if (ormContext == null) {
            return findIdentity(lookup)
                    .toCompletableFuture()
                    .join()
                    .map(identity -> profileForRepositories(identity, query));
        }
        return ormContext.runInTransaction(session -> {
            PlayerEntity player = findPlayer(session, lookup);
            if (player == null || player.getId() == null) {
                return Optional.empty();
            }
            return Optional.of(profileFor(session, PlayerRepository.toIdentity(player), query));
        });
    }

    private Optional<PlayerProfile> findProfileProjection(PlayerIdentity identity, PlayerProfileQuery query) {
        if (ormContext == null) {
            return Optional.of(profileForRepositories(identity, query));
        }
        return ormContext.runInTransaction(session -> {
            PlayerEntity player = session.find(PlayerEntity.class, identity.playerId());
            if (player == null) {
                return Optional.empty();
            }
            return Optional.of(profileFor(session, PlayerRepository.toIdentity(player), query));
        });
    }

    private PlayerProfile profileForRepositories(PlayerIdentity identity, PlayerProfileQuery query) {
        long playerId = identity.playerId();
        return new PlayerProfile(
                identity,
                findLanguageSync(playerId),
                findNicknameSync(playerId),
                findConnectionSync(playerId),
                findOnlineStatusSync(playerId),
                findActivitySync(playerId),
                findPlaytimeSync(playerId, query.asOf()),
                findNameHistorySync(playerId, query.nameHistoryLimit())
        );
    }

    private PlayerProfile profileFor(Session session, PlayerIdentity identity, PlayerProfileQuery query) {
        long playerId = identity.playerId();
        return new PlayerProfile(
                identity,
                findLanguageInSession(session, playerId),
                findNicknameInSession(session, playerId),
                findConnectionInSession(session, playerId),
                findOnlineStatusInSession(session, playerId),
                findActivityInSession(session, playerId),
                findPlaytimeInSession(session, identity, query.asOf()),
                findNameHistoryInSession(session, playerId, query.nameHistoryLimit())
        );
    }

    private PlayerEntity findPlayer(Session session, PlayerLookup lookup) {
        return switch (lookup.type()) {
            case PLAYER_ID -> session.find(PlayerEntity.class, lookup.playerId());
            case UUID -> findPlayerByUuid(session, lookup.uuid().toString());
            case USERNAME -> findPlayerByUsernameIgnoreCase(session, lookup.text());
            case IDENTIFIER -> {
                String uuid = normalizeUuid(lookup.text());
                yield uuid == null ? findPlayerByUsernameIgnoreCase(session, lookup.text()) : findPlayerByUuid(session, uuid);
            }
        };
    }

    private PlayerEntity findPlayerByUuid(Session session, String uuid) {
        return session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                .setParameter("uuid", uuid)
                .setMaxResults(1)
                .uniqueResult();
    }

    private PlayerEntity findPlayerByUsernameIgnoreCase(Session session, String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return session.createQuery(
                        "SELECT p FROM PlayerEntity p WHERE LOWER(p.username) = :username",
                        PlayerEntity.class
                )
                .setParameter("username", username.trim().toLowerCase(Locale.ROOT))
                .setMaxResults(1)
                .uniqueResult();
    }

    private Optional<PlayerLanguageSettings> findLanguageInSession(Session session, long playerId) {
        if (languageRepository == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.find(PlayerLanguageEntity.class, playerId))
                .map(RepositoryPlayerData::toLanguageSettings);
    }

    private Optional<String> findNicknameInSession(Session session, long playerId) {
        if (nicknameRepository == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.find(PlayerNicknameEntity.class, playerId))
                .map(PlayerNicknameEntity::getNickname);
    }

    private Optional<PlayerConnectionSnapshot> findConnectionInSession(Session session, long playerId) {
        if (connectionInfoRepository == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.find(PlayerConnectionInfoEntity.class, playerId))
                .map(RepositoryPlayerData::toConnectionSnapshot);
    }

    private Optional<PlayerOnlineSnapshot> findOnlineStatusInSession(Session session, long playerId) {
        if (onlineStatusRepository == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.find(PlayerOnlineStatusEntity.class, playerId))
                .map(RepositoryPlayerData::toOnlineSnapshot);
    }

    private Optional<PlayerActivitySnapshot> findActivityInSession(Session session, long playerId) {
        if (activitySummaryRepository == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.find(PlayerActivitySummaryEntity.class, playerId))
                .map(RepositoryPlayerData::toActivitySnapshot);
    }

    private Optional<PlayerPlaytimeSnapshot> findPlaytimeInSession(
            Session session,
            PlayerIdentity identity,
            Instant asOf
    ) {
        if (playtimeRepository == null) {
            return Optional.empty();
        }
        long playerId = identity.playerId();
        List<PlayerPlaytimeEntity> aggregates = session.createQuery(
                        "SELECT p FROM PlayerPlaytimeEntity p " +
                                "WHERE p.player.id = :playerId " +
                                "ORDER BY p.trackedMillis DESC, p.gamemodeKey ASC",
                        PlayerPlaytimeEntity.class
                )
                .setParameter("playerId", playerId)
                .list();
        Optional<PlayerPlaytimeSegmentEntity> openSegment = session.createQuery(
                        "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                "WHERE s.player.id = :playerId " +
                                "AND s.endedAt IS NULL AND s.session.endedAt IS NULL " +
                                "ORDER BY s.startedAt DESC, s.id DESC",
                        PlayerPlaytimeSegmentEntity.class
                )
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .uniqueResultOptional();

        Map<String, GamemodeSnapshotAccumulator> byGamemode = new LinkedHashMap<>();
        long trackedTotalMillis = 0L;
        long networkTotalMillis = 0L;
        for (PlayerPlaytimeEntity aggregate : aggregates) {
            boolean counted = !playtimeExcludedGamemodeKeys.contains(aggregate.getGamemodeKey());
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
            boolean counted = !playtimeExcludedGamemodeKeys.contains(segment.getGamemodeKey());
            trackedTotalMillis += liveDeltaMillis;
            if (counted) {
                networkTotalMillis += liveDeltaMillis;
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
                playerId,
                identity.uuid().toString(),
                identity.username(),
                trackedTotalMillis,
                networkTotalMillis,
                asOf,
                gamemodeSnapshots
        ));
    }

    private List<PlayerNameHistoryEntry> findNameHistoryInSession(Session session, long playerId, int limit) {
        if (nameHistoryRepository == null || limit == 0) {
            return List.of();
        }
        return session.createQuery(
                        "SELECT h FROM PlayerNameHistoryEntity h " +
                                "WHERE h.player.id = :playerId " +
                                "ORDER BY h.lastSeenAt ASC, h.id ASC",
                        PlayerNameHistoryEntity.class
                )
                .setParameter("playerId", playerId)
                .setMaxResults(Math.max(1, limit))
                .list()
                .stream()
                .map(RepositoryPlayerData::toNameHistoryEntry)
                .flatMap(Optional::stream)
                .toList();
    }

    private static PlayerLanguageSettings toLanguageSettings(PlayerLanguageEntity entity) {
        return new PlayerLanguageSettings(entity.getPlayerId(), entity.getLanguage(), entity.getEffectiveLanguage());
    }

    private static PlayerConnectionSnapshot toConnectionSnapshot(PlayerConnectionInfoEntity entity) {
        return new PlayerConnectionSnapshot(
                entity.getPlayerId(),
                entity.getIpAddress(),
                entity.getFirstConnectionAt(),
                entity.getLastConnectionAt(),
                entity.getLastDisconnectAt(),
                entity.getVirtualHost()
        );
    }

    private static Optional<PlayerNameHistoryEntry> toNameHistoryEntry(PlayerNameHistoryEntity entity) {
        if (entity.getId() == null || entity.getPlayer() == null || entity.getPlayer().getId() == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerNameHistoryEntry(
                entity.getId(),
                entity.getPlayer().getId(),
                entity.getUsername(),
                entity.getLastSeenAt()
        ));
    }

    private static PlayerOnlineSnapshot toOnlineSnapshot(PlayerOnlineStatusEntity entity) {
        return new PlayerOnlineSnapshot(
                entity.getPlayerId(),
                entity.isOnline(),
                entity.getCurrentServer(),
                entity.getPreviousServer()
        );
    }

    private static PlayerActivitySnapshot toActivitySnapshot(PlayerActivitySummaryEntity entity) {
        return new PlayerActivitySnapshot(
                entity.getPlayerId(),
                entity.getFirstSeenAt(),
                entity.getLastSeenAt(),
                entity.getLastLoginAt(),
                entity.getLastLogoutAt()
        );
    }

    private static void requireRepository(Object repository, DataRegistryFeature feature) {
        if (repository == null) {
            throw new IllegalStateException(feature + " data is unavailable.");
        }
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

    private static String normalizeGamemodeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
