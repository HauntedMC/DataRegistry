package nl.hauntedmc.dataregistry.api.player;

/**
 * Controls player-facing disclosure of historical and statistical player data.
 *
 * <p>The absence of a persisted setting is {@link #PUBLIC}.</p>
 */
public enum PlayerDataVisibility {
    PUBLIC,
    FRIENDS,
    PRIVATE
}
