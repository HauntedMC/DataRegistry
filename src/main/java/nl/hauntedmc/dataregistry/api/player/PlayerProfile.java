package nl.hauntedmc.dataregistry.api.player;

import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable aggregate of DataRegistry-owned player data for read-side screens and commands.
 * <p>
 * The profile only contains data owned by DataRegistry. Feature-owned information such as sanctions,
 * friends, 2FA, vanish state, glow state, or logs remains in the feature that owns those tables.
 *
 * @param identity    canonical player identity.
 * @param language    stored language preference, when the language feature is enabled and present.
 * @param nickname    stored nickname, when the nickname feature is enabled and present.
 * @param connection  latest connection metadata, when the connection-info feature is enabled and present.
 * @param online      current online snapshot, when the online-status feature is enabled and present.
 * @param activity    activity summary, when the activity-summary feature is enabled and present.
 * @param playtime    playtime snapshot, when the playtime feature is enabled and present.
 * @param nameHistory chronological username history entries.
 */
public record PlayerProfile(
        PlayerIdentity identity,
        Optional<PlayerLanguageSettings> language,
        Optional<String> nickname,
        Optional<PlayerConnectionSnapshot> connection,
        Optional<PlayerOnlineSnapshot> online,
        Optional<PlayerActivitySnapshot> activity,
        Optional<PlayerPlaytimeSnapshot> playtime,
        List<PlayerNameHistoryEntry> nameHistory
) {

    /**
     * Creates a profile snapshot with defensive copies for optional values and lists.
     */
    public PlayerProfile {
        Objects.requireNonNull(identity, "identity must not be null");
        language = requireOptional(language, "language");
        nickname = requireOptional(nickname, "nickname");
        connection = requireOptional(connection, "connection");
        online = requireOptional(online, "online");
        activity = requireOptional(activity, "activity");
        playtime = requireOptional(playtime, "playtime");
        nameHistory = List.copyOf(Objects.requireNonNull(nameHistory, "nameHistory must not be null"));
    }

    /**
     * Returns whether DataRegistry currently marks this player online.
     */
    public boolean isOnline() {
        return online.map(PlayerOnlineSnapshot::online).orElse(false);
    }

    /**
     * Returns the current server from DataRegistry's online status, when available.
     */
    public Optional<String> currentServer() {
        return online.flatMap(status -> Optional.ofNullable(status.currentServer()));
    }

    private static <T> Optional<T> requireOptional(Optional<T> optional, String name) {
        return Objects.requireNonNull(optional, name + " must not be null");
    }
}
