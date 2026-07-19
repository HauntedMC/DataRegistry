package nl.hauntedmc.dataregistry.api.player;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Player-centric facade for DataRegistry-owned player data.
 * <p>
 * This is the preferred API for downstream plugins. It exposes stable identities and immutable
 * snapshots instead of ORM entities, while keeping authoritative player creation and username
 * updates inside DataRegistry lifecycle handling.
 * <p>
 * Methods whose names start with {@code find} may perform database I/O. Call them from an async
 * context on Paper or Velocity event paths. {@code whenReady} only observes lifecycle state, but its
 * callbacks may run on a DataRegistry lifecycle thread.
 */
public interface PlayerData {

    /**
     * Returns the canonical identity directory used by this facade.
     */
    PlayerDirectory identities();

    /**
     * Returns whether a DataRegistry-owned player data feature is enabled.
     */
    boolean supports(DataRegistryFeature feature);

    /**
     * Returns the active cached identity for a player without querying persistence.
     */
    Optional<PlayerIdentity> activeIdentity(UUID uuid);

    /**
     * Looks up a persisted identity by UUID without creating or updating a player row.
     */
    Optional<PlayerIdentity> findIdentity(UUID uuid);

    /**
     * Looks up a persisted identity by UUID string without creating or updating a player row.
     */
    Optional<PlayerIdentity> findIdentity(String uuid);

    /**
     * Looks up a persisted identity by case-insensitive username.
     */
    Optional<PlayerIdentity> findIdentityByUsername(String username);

    /**
     * Returns a stable player id by UUID when the canonical row already exists.
     */
    Optional<Long> findPlayerId(UUID uuid);

    /**
     * Returns a stable player id by UUID string when the canonical row already exists.
     */
    Optional<Long> findPlayerId(String uuid);

    /**
     * Completes when the platform lifecycle has finished preparing the active identity.
     */
    CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid);

    /**
     * Completes when the platform lifecycle has finished preparing the active identity.
     */
    CompletableFuture<Optional<PlayerIdentity>> whenReady(String uuid);

    /**
     * Returns the stored language preference and effective language for a player.
     */
    Optional<PlayerLanguageSettings> findLanguage(long playerId);

    /**
     * Returns the stored language preference and effective language for a player.
     */
    Optional<PlayerLanguageSettings> findLanguage(UUID uuid);

    /**
     * Stores a language preference for an existing player id.
     */
    void saveLanguage(long playerId, String language, String effectiveLanguage);

    /**
     * Stores a language preference for an existing player UUID.
     *
     * @return {@code true} when an existing player row was found and updated.
     */
    boolean saveLanguage(UUID uuid, String language, String effectiveLanguage);

    /**
     * Removes a stored language preference for an existing player id.
     */
    void clearLanguage(long playerId);

    /**
     * Returns a player's stored nickname.
     */
    Optional<String> findNickname(long playerId);

    /**
     * Returns a player's stored nickname.
     */
    Optional<String> findNickname(UUID uuid);

    /**
     * Stores a nickname for an existing player id.
     */
    void saveNickname(long playerId, String nickname);

    /**
     * Stores a nickname for an existing player UUID.
     *
     * @return {@code true} when an existing player row was found and updated.
     */
    boolean saveNickname(UUID uuid, String nickname);

    /**
     * Removes a stored nickname for an existing player id.
     */
    void clearNickname(long playerId);

    /**
     * Returns latest connection metadata for a player.
     */
    Optional<PlayerConnectionSnapshot> findConnection(long playerId);

    /**
     * Returns latest connection metadata for a player.
     */
    Optional<PlayerConnectionSnapshot> findConnection(UUID uuid);

    /**
     * Returns identities whose latest stored IP address matches {@code ipAddress}.
     */
    List<PlayerIdentity> findIdentitiesByLastIpAddress(String ipAddress, Long excludePlayerId);

    /**
     * Returns player ids whose latest stored IP address matches {@code ipAddress}.
     */
    List<Long> findPlayerIdsByLastIpAddress(String ipAddress, Long excludePlayerId);

    /**
     * Returns usernames whose latest stored IP address matches {@code ipAddress}.
     */
    List<String> findUsernamesByLastIpAddress(String ipAddress, Long excludePlayerId);

    /**
     * Returns chronological username history rows for a player.
     */
    List<PlayerNameHistoryEntry> findNameHistory(long playerId, int limit);

    /**
     * Returns chronological username history rows for a player.
     */
    List<PlayerNameHistoryEntry> findNameHistory(UUID uuid, int limit);

    /**
     * Resolves a player's current username and returns chronological username history rows.
     */
    List<PlayerNameHistoryEntry> findNameHistoryByCurrentUsername(String username, int limit);

    /**
     * Returns the player's current online-status snapshot.
     */
    Optional<PlayerOnlineSnapshot> findOnlineStatus(long playerId);

    /**
     * Returns online players across the network.
     */
    List<PlayerOnlineSnapshot> findOnlinePlayers(int limit);

    /**
     * Returns online players currently on one backend server.
     */
    List<PlayerOnlineSnapshot> findOnlinePlayersByServer(String serverName, int limit);

    /**
     * Returns the player's activity summary.
     */
    Optional<PlayerActivitySnapshot> findActivity(long playerId);

    /**
     * Returns recently seen players ordered newest first.
     */
    List<PlayerActivitySnapshot> findRecentlySeen(int limit);

    /**
     * Returns a playtime snapshot for a player.
     */
    Optional<PlayerPlaytimeSnapshot> findPlaytime(long playerId);

    /**
     * Returns a playtime snapshot for a player as of a specific instant.
     */
    Optional<PlayerPlaytimeSnapshot> findPlaytime(long playerId, Instant asOf);

    /**
     * Returns top players by network-total playtime.
     */
    List<PlayerPlaytimeLeaderboardEntry> findTopPlaytime(int limit);

    /**
     * Returns top players for one gamemode key.
     */
    List<PlayerPlaytimeLeaderboardEntry> findTopPlaytimeByGamemode(String gamemodeKey, int limit);

    /**
     * Returns all gamemode keys with tracked playtime.
     */
    List<String> findTrackedGamemodeKeys();
}
