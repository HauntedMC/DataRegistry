package nl.hauntedmc.dataregistry.backend.player;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.entities.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerActivitySnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerConnectionSnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageSettings;
import nl.hauntedmc.dataregistry.api.player.PlayerNameHistoryEntry;
import nl.hauntedmc.dataregistry.api.player.PlayerOnlineSnapshot;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;
import nl.hauntedmc.dataregistry.api.repository.PlayerActivitySummaryRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerConnectionInfoRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerLanguageRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerNameHistoryRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerNicknameRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerOnlineStatusRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerPlaytimeRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository-backed implementation of the public player-data facade.
 */
public final class RepositoryPlayerData implements PlayerData {

    private final PlayerDirectory playerDirectory;
    private final Set<DataRegistryFeature> enabledFeatures;
    private final PlayerActivitySummaryRepository activitySummaryRepository;
    private final PlayerOnlineStatusRepository onlineStatusRepository;
    private final PlayerConnectionInfoRepository connectionInfoRepository;
    private final PlayerLanguageRepository languageRepository;
    private final PlayerNicknameRepository nicknameRepository;
    private final PlayerNameHistoryRepository nameHistoryRepository;
    private final PlayerPlaytimeRepository playtimeRepository;

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
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory must not be null");
        this.enabledFeatures = Set.copyOf(Objects.requireNonNull(enabledFeatures, "enabledFeatures must not be null"));
        this.activitySummaryRepository = activitySummaryRepository;
        this.onlineStatusRepository = onlineStatusRepository;
        this.connectionInfoRepository = connectionInfoRepository;
        this.languageRepository = languageRepository;
        this.nicknameRepository = nicknameRepository;
        this.nameHistoryRepository = nameHistoryRepository;
        this.playtimeRepository = playtimeRepository;
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
    public Optional<PlayerIdentity> activeIdentity(UUID uuid) {
        return playerDirectory.getActiveIdentity(uuid);
    }

    @Override
    public Optional<PlayerIdentity> findIdentity(UUID uuid) {
        return playerDirectory.findByUuid(uuid);
    }

    @Override
    public Optional<PlayerIdentity> findIdentity(String uuid) {
        return playerDirectory.findByUuid(uuid);
    }

    @Override
    public Optional<PlayerIdentity> findIdentityByUsername(String username) {
        return playerDirectory.findByUsernameIgnoreCase(username);
    }

    @Override
    public Optional<Long> findPlayerId(UUID uuid) {
        return activeIdentity(uuid)
                .or(() -> findIdentity(uuid))
                .map(PlayerIdentity::playerId);
    }

    @Override
    public Optional<Long> findPlayerId(String uuid) {
        return playerDirectory.getActiveIdentity(uuid)
                .or(() -> playerDirectory.findByUuid(uuid))
                .map(PlayerIdentity::playerId);
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
    public Optional<PlayerLanguageSettings> findLanguage(long playerId) {
        if (playerId <= 0L || languageRepository == null) {
            return Optional.empty();
        }
        return languageRepository.findByPlayerId(playerId).map(RepositoryPlayerData::toLanguageSettings);
    }

    @Override
    public Optional<PlayerLanguageSettings> findLanguage(UUID uuid) {
        return findPlayerId(uuid).flatMap(this::findLanguage);
    }

    @Override
    public void saveLanguage(long playerId, String language, String effectiveLanguage) {
        requireRepository(languageRepository, DataRegistryFeature.LANGUAGE);
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
        languageRepository.saveOrUpdate(playerId, language, effectiveLanguage);
    }

    @Override
    public boolean saveLanguage(UUID uuid, String language, String effectiveLanguage) {
        Optional<Long> playerId = findPlayerId(uuid);
        playerId.ifPresent(value -> saveLanguage(value, language, effectiveLanguage));
        return playerId.isPresent();
    }

    @Override
    public void clearLanguage(long playerId) {
        if (languageRepository == null || playerId <= 0L) {
            return;
        }
        languageRepository.deleteByPlayerId(playerId);
    }

    @Override
    public Optional<String> findNickname(long playerId) {
        if (playerId <= 0L || nicknameRepository == null) {
            return Optional.empty();
        }
        return nicknameRepository.findNicknameByPlayerId(playerId);
    }

    @Override
    public Optional<String> findNickname(UUID uuid) {
        return findPlayerId(uuid).flatMap(this::findNickname);
    }

    @Override
    public void saveNickname(long playerId, String nickname) {
        requireRepository(nicknameRepository, DataRegistryFeature.NICKNAMES);
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
        nicknameRepository.saveOrUpdate(playerId, nickname);
    }

    @Override
    public boolean saveNickname(UUID uuid, String nickname) {
        Optional<Long> playerId = findPlayerId(uuid);
        playerId.ifPresent(value -> saveNickname(value, nickname));
        return playerId.isPresent();
    }

    @Override
    public void clearNickname(long playerId) {
        if (nicknameRepository == null || playerId <= 0L) {
            return;
        }
        nicknameRepository.deleteByPlayerId(playerId);
    }

    @Override
    public Optional<PlayerConnectionSnapshot> findConnection(long playerId) {
        if (playerId <= 0L || connectionInfoRepository == null) {
            return Optional.empty();
        }
        return connectionInfoRepository.findByPlayerId(playerId).map(RepositoryPlayerData::toConnectionSnapshot);
    }

    @Override
    public Optional<PlayerConnectionSnapshot> findConnection(UUID uuid) {
        return findPlayerId(uuid).flatMap(this::findConnection);
    }

    @Override
    public List<PlayerIdentity> findIdentitiesByLastIpAddress(String ipAddress, Long excludePlayerId) {
        if (connectionInfoRepository == null) {
            return List.of();
        }
        return connectionInfoRepository.findIdentitiesByLastIpAddress(ipAddress, excludePlayerId);
    }

    @Override
    public List<Long> findPlayerIdsByLastIpAddress(String ipAddress, Long excludePlayerId) {
        if (connectionInfoRepository == null) {
            return List.of();
        }
        return connectionInfoRepository.findPlayerIdsByLastIpAddress(ipAddress, excludePlayerId);
    }

    @Override
    public List<String> findUsernamesByLastIpAddress(String ipAddress, Long excludePlayerId) {
        if (connectionInfoRepository == null) {
            return List.of();
        }
        return connectionInfoRepository.findUsernamesByLastIpAddress(ipAddress, excludePlayerId);
    }

    @Override
    public List<PlayerNameHistoryEntry> findNameHistory(long playerId, int limit) {
        if (playerId <= 0L || nameHistoryRepository == null) {
            return List.of();
        }
        return nameHistoryRepository.findChronologicalByPlayer(playerId, limit)
                .stream()
                .map(RepositoryPlayerData::toNameHistoryEntry)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public List<PlayerNameHistoryEntry> findNameHistory(UUID uuid, int limit) {
        return findPlayerId(uuid)
                .map(playerId -> findNameHistory(playerId, limit))
                .orElseGet(List::of);
    }

    @Override
    public List<PlayerNameHistoryEntry> findNameHistoryByCurrentUsername(String username, int limit) {
        return findIdentityByUsername(username)
                .map(PlayerIdentity::playerId)
                .map(playerId -> findNameHistory(playerId, limit))
                .orElseGet(List::of);
    }

    @Override
    public Optional<PlayerOnlineSnapshot> findOnlineStatus(long playerId) {
        if (playerId <= 0L || onlineStatusRepository == null) {
            return Optional.empty();
        }
        return onlineStatusRepository.findByPlayerId(playerId).map(RepositoryPlayerData::toOnlineSnapshot);
    }

    @Override
    public List<PlayerOnlineSnapshot> findOnlinePlayers(int limit) {
        if (onlineStatusRepository == null) {
            return List.of();
        }
        return onlineStatusRepository.findOnlinePlayers(limit)
                .stream()
                .map(RepositoryPlayerData::toOnlineSnapshot)
                .toList();
    }

    @Override
    public List<PlayerOnlineSnapshot> findOnlinePlayersByServer(String serverName, int limit) {
        if (onlineStatusRepository == null) {
            return List.of();
        }
        return onlineStatusRepository.findOnlinePlayersByServer(serverName, limit)
                .stream()
                .map(RepositoryPlayerData::toOnlineSnapshot)
                .toList();
    }

    @Override
    public Optional<PlayerActivitySnapshot> findActivity(long playerId) {
        if (playerId <= 0L || activitySummaryRepository == null) {
            return Optional.empty();
        }
        return activitySummaryRepository.findByPlayerId(playerId).map(RepositoryPlayerData::toActivitySnapshot);
    }

    @Override
    public List<PlayerActivitySnapshot> findRecentlySeen(int limit) {
        if (activitySummaryRepository == null) {
            return List.of();
        }
        return activitySummaryRepository.findRecentlySeen(limit)
                .stream()
                .map(RepositoryPlayerData::toActivitySnapshot)
                .toList();
    }

    @Override
    public Optional<PlayerPlaytimeSnapshot> findPlaytime(long playerId) {
        if (playerId <= 0L || playtimeRepository == null) {
            return Optional.empty();
        }
        return playtimeRepository.findSnapshotByPlayerId(playerId);
    }

    @Override
    public Optional<PlayerPlaytimeSnapshot> findPlaytime(long playerId, Instant asOf) {
        if (playerId <= 0L || playtimeRepository == null) {
            return Optional.empty();
        }
        return playtimeRepository.findSnapshotByPlayerId(playerId, asOf);
    }

    @Override
    public List<PlayerPlaytimeLeaderboardEntry> findTopPlaytime(int limit) {
        if (playtimeRepository == null) {
            return List.of();
        }
        return playtimeRepository.findTopPlayersByNetworkTotal(limit);
    }

    @Override
    public List<PlayerPlaytimeLeaderboardEntry> findTopPlaytimeByGamemode(String gamemodeKey, int limit) {
        if (playtimeRepository == null) {
            return List.of();
        }
        return playtimeRepository.findTopPlayersByGamemode(gamemodeKey, limit);
    }

    @Override
    public List<String> findTrackedGamemodeKeys() {
        if (playtimeRepository == null) {
            return List.of();
        }
        return playtimeRepository.findTrackedGamemodeKeys();
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
}
