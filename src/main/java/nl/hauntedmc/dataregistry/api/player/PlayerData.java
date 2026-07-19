package nl.hauntedmc.dataregistry.api.player;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Player-centric facade for DataRegistry-owned player data.
 * <p>
 * Public persistence reads are asynchronous by construction. Returned stages are backed by
 * DataRegistry's query executor and may complete exceptionally when cancelled or when the configured
 * query deadline is exceeded. Only explicitly named cached methods are synchronous.
 */
public interface PlayerData {

    PlayerDirectory identities();

    boolean supports(DataRegistryFeature feature);

    /**
     * Returns the active cached identity for a player without querying persistence.
     */
    Optional<PlayerIdentity> findActiveIdentityCached(UUID uuid);

    CompletionStage<Optional<PlayerIdentity>> findIdentity(PlayerLookup lookup);

    CompletionStage<Map<PlayerLookup, Optional<PlayerIdentity>>> findIdentities(Collection<PlayerLookup> lookups);

    CompletionStage<Optional<PlayerIdentity>> findIdentity(UUID uuid);

    CompletionStage<Optional<PlayerIdentity>> findIdentity(long playerId);

    CompletionStage<Optional<PlayerIdentity>> findIdentity(String uuid);

    CompletionStage<Optional<PlayerIdentity>> findIdentityByUsername(String username);

    CompletionStage<Optional<PlayerIdentity>> findIdentityByIdentifier(String identifier);

    CompletionStage<List<PlayerIdentity>> findIdentitiesByUsernamePrefix(String prefix, int limit);

    CompletionStage<PlayerPage<PlayerIdentity>> findIdentitiesByUsernamePrefix(String prefix, PlayerPageRequest pageRequest);

    CompletionStage<Optional<Long>> findPlayerId(UUID uuid);

    CompletionStage<Optional<Long>> findPlayerId(String uuid);

    CompletionStage<Optional<Long>> findPlayerIdByIdentifier(String identifier);

    CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid);

    CompletableFuture<Optional<PlayerIdentity>> whenReady(String uuid);

    CompletionStage<Optional<PlayerLanguageSettings>> findLanguage(long playerId);

    CompletionStage<Optional<PlayerLanguageSettings>> findLanguage(UUID uuid);

    CompletionStage<Void> saveLanguage(long playerId, String language, String effectiveLanguage);

    CompletionStage<Boolean> saveLanguage(UUID uuid, String language, String effectiveLanguage);

    CompletionStage<Void> clearLanguage(long playerId);

    CompletionStage<Optional<String>> findNickname(long playerId);

    CompletionStage<Optional<String>> findNickname(UUID uuid);

    CompletionStage<Void> saveNickname(long playerId, String nickname);

    CompletionStage<Boolean> saveNickname(UUID uuid, String nickname);

    CompletionStage<Void> clearNickname(long playerId);

    CompletionStage<Optional<PlayerConnectionSnapshot>> findConnection(long playerId);

    CompletionStage<Optional<PlayerConnectionSnapshot>> findConnection(UUID uuid);

    CompletionStage<List<PlayerIdentity>> findIdentitiesByLastIpAddress(String ipAddress, Long excludePlayerId);

    CompletionStage<List<Long>> findPlayerIdsByLastIpAddress(String ipAddress, Long excludePlayerId);

    CompletionStage<List<String>> findUsernamesByLastIpAddress(String ipAddress, Long excludePlayerId);

    CompletionStage<List<PlayerIdentity>> findIdentitiesSharingLastIp(long playerId);

    CompletionStage<List<String>> findUsernamesSharingLastIp(long playerId);

    CompletionStage<List<PlayerNameHistoryEntry>> findNameHistory(long playerId, int limit);

    CompletionStage<List<PlayerNameHistoryEntry>> findNameHistory(UUID uuid, int limit);

    CompletionStage<List<PlayerNameHistoryEntry>> findNameHistoryByCurrentUsername(String username, int limit);

    CompletionStage<Optional<PlayerOnlineSnapshot>> findOnlineStatus(long playerId);

    CompletionStage<List<PlayerOnlineSnapshot>> findOnlinePlayers(int limit);

    CompletionStage<List<PlayerOnlineSnapshot>> findOnlinePlayersByServer(String serverName, int limit);

    CompletionStage<Optional<PlayerActivitySnapshot>> findActivity(long playerId);

    CompletionStage<List<PlayerActivitySnapshot>> findRecentlySeen(int limit);

    CompletionStage<Optional<PlayerPlaytimeSnapshot>> findPlaytime(long playerId);

    CompletionStage<Optional<PlayerPlaytimeSnapshot>> findPlaytime(long playerId, Instant asOf);

    CompletionStage<List<PlayerPlaytimeLeaderboardEntry>> findTopPlaytime(int limit);

    CompletionStage<List<PlayerPlaytimeLeaderboardEntry>> findTopPlaytimeByGamemode(String gamemodeKey, int limit);

    CompletionStage<List<String>> findTrackedGamemodeKeys();

    CompletionStage<PlayerProfileResult> findProfile(PlayerLookup lookup, PlayerProfileQuery query);

    CompletionStage<Optional<PlayerProfile>> findProfile(PlayerIdentity identity, int nameHistoryLimit);

    CompletionStage<Optional<PlayerProfile>> findProfile(long playerId, int nameHistoryLimit);

    CompletionStage<Optional<PlayerProfile>> findProfile(UUID uuid, int nameHistoryLimit);

    CompletionStage<Optional<PlayerProfile>> findProfileByUsername(String username, int nameHistoryLimit);

    CompletionStage<Optional<PlayerProfile>> findProfileByIdentifier(String identifier, int nameHistoryLimit);
}
