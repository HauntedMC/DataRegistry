package nl.hauntedmc.dataregistry.api.player;

/**
 * Immutable online-state snapshot for a player.
 *
 * @param playerId       stable DataRegistry player id.
 * @param online         whether the player is currently marked online.
 * @param currentServer  current backend server name, when known.
 * @param previousServer previous backend server name, when known.
 */
public record PlayerOnlineSnapshot(
        long playerId,
        boolean online,
        String currentServer,
        String previousServer
) {
}
