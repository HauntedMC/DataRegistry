package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.platform.bukkit.util.BukkitPlayerAdapter;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerStatusListener implements Listener {

    private final PlayerService playerService;
    private final BukkitDataRegistry plugin;

    public PlayerStatusListener(BukkitDataRegistry plugin, PlayerService playerService) {
        this.plugin = plugin;
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Introduce delay to favor velocity db entry creation
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            PlayerEntity temp = BukkitPlayerAdapter.fromPlatformPlayer(event.getPlayer());
            playerService.onPlayerJoin(temp);
        }, 4L); // 4 ticks = ~200ms
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String username = event.getPlayer().getName();
        String uuid = event.getPlayer().getUniqueId().toString();
        playerService.onPlayerQuit(username, uuid);
    }
}
