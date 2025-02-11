package nl.hauntedmc.dataregistry.service;

import nl.hauntedmc.dataregistry.DataRegistry;
import nl.hauntedmc.dataregistry.entities.GamePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerService {

    // A thread-safe map to hold active players.
    private final Map<String, GamePlayer> activePlayers = new ConcurrentHashMap<>();
    private final DataRegistry plugin;

    public PlayerService(DataRegistry plugin) {
        this.plugin = plugin;
    }


    public void onPlayerJoin(Player player) {
        final String uuid = player.getUniqueId().toString();
        final String playerName = player.getName();

        // Run a transaction to load or create the player record.
        GamePlayer gp = plugin.getORM().runInTransaction(session -> {
            GamePlayer found = session.find(GamePlayer.class, uuid);
            if (found == null) {
                found = new GamePlayer();
                found.setUuid(uuid);
            }
            // Update username and set as online.
            found.setUsername(playerName);
            found.setIsOnline(1);
            session.persist(found);  // persist or merge depending on your implementation
            return found;
        });
        activePlayers.put(uuid, gp);
    }

    public void onPlayerQuit(Player player) {
        final String uuid = player.getUniqueId().toString();
        final String playerName = player.getName();

        plugin.getORM().runInTransaction(session -> {
            GamePlayer gp = session.find(GamePlayer.class, uuid);
            if (gp != null) {
                // Update the username (if needed) and mark offline.
                gp.setUsername(playerName);
                gp.setIsOnline(0);
            }
            return null;
        });
        activePlayers.remove(uuid);
    }

    public Map<String, GamePlayer> getActivePlayers() {
        return activePlayers;
    }
}
