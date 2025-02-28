package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.platform.bukkit.BukkitPlayerAdapter;
import nl.hauntedmc.dataregistry.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.bukkit.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.bukkit.service.PlayerStatusService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerStatusListener implements Listener {

    private final PlayerService playerService;
    private final PlayerStatusService statusService;
    private final String serverName;

    public PlayerStatusListener(PlayerService playerService, PlayerStatusService statusService, String serverName) {
        this.playerService = playerService;
        this.statusService = statusService;
        this.serverName = serverName;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Convert live player to a temporary entity.
        PlayerEntity temp = BukkitPlayerAdapter.fromPlatformPlayer(event.getPlayer());
        // Get (or create) the persistent record.
        PlayerEntity persistent = playerService.onPlayerJoin(temp);
        // Update online status.
        statusService.updateStatusOnJoin(persistent, serverName);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        playerService.getActivePlayer(uuid).ifPresent(persistent ->
                statusService.updateStatusOnQuit(persistent, serverName)
        );
        playerService.onPlayerQuit(uuid);
    }
}
