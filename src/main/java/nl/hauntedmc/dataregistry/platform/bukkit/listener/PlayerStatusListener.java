package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.platform.bukkit.util.BukkitPlayerAdapter;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerStatusListener implements Listener {

    private final PlayerService playerService;

    public PlayerStatusListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerEntity temp = BukkitPlayerAdapter.fromPlatformPlayer(event.getPlayer());
        playerService.onPlayerJoin(temp);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        playerService.onPlayerQuit(uuid);
    }
}
