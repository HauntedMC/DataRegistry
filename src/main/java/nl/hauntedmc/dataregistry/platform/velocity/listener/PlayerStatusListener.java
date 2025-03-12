package nl.hauntedmc.dataregistry.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.velocity.util.VelocityPlayerAdapter;

public class PlayerStatusListener {

    private final PlayerService playerService;
    private final PlayerStatusService statusService;

    public PlayerStatusListener(PlayerService playerService, PlayerStatusService statusService) {
        this.playerService = playerService;
        this.statusService = statusService;
    }

    @Subscribe(priority = 10, async = true)
    public void onPlayerJoin(ServerConnectedEvent event) {
        PlayerEntity temp = VelocityPlayerAdapter.fromPlatformPlayer(event.getPlayer());
        PlayerEntity persistent = playerService.onPlayerJoin(temp);
        statusService.updateStatus(persistent, event.getServer().getServerInfo().getName());
    }

    @Subscribe(priority = 10, async = true)
    public void onPlayerQuit(DisconnectEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        playerService.getActivePlayer(uuid).ifPresent(statusService::updateStatusOnQuit);
        playerService.onPlayerQuit(uuid);
    }
}
