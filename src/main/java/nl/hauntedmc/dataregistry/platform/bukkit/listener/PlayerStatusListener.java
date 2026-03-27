package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.bukkit.util.BukkitPlayerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public class PlayerStatusListener implements Listener {

    private final PlayerService playerService;
    private final BukkitDataRegistry plugin;
    private final long joinDelayTicks;

    public PlayerStatusListener(BukkitDataRegistry plugin, PlayerService playerService, int joinDelayTicks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.joinDelayTicks = joinDelayTicks;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Runnable job = () -> {
            try {
                PlayerEntity temp = BukkitPlayerAdapter.fromPlatformPlayer(event.getPlayer());
                playerService.onPlayerJoin(temp);
            } catch (RuntimeException exception) {
                plugin.getPlatformLogger().error("Failed to process Bukkit player join event.", exception);
            }
        };

        if (joinDelayTicks > 0L) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, job, joinDelayTicks);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, job);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerService.onPlayerQuit(
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId().toString()
        );
    }
}
