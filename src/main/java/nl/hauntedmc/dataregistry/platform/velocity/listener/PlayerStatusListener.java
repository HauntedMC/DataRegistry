package nl.hauntedmc.dataregistry.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.velocity.util.VelocityPlayerAdapter;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

public class PlayerStatusListener {

    private final PlayerService playerService;
    private final PlayerStatusService statusService;
    private final PlayerConnectionInfoService connectionService;
    private final PlayerSessionService sessionService;

    public PlayerStatusListener(PlayerService playerService,
                                PlayerStatusService statusService,
                                PlayerConnectionInfoService connectionService,
                                PlayerSessionService sessionService) {
        this.playerService = playerService;
        this.statusService = statusService;
        this.connectionService = connectionService;
        this.sessionService = sessionService;
    }

    @Subscribe(priority = 10, async = true)
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();

        // Persist & cache player
        PlayerEntity temp = VelocityPlayerAdapter.fromPlatformPlayer(player);
        PlayerEntity persistent = playerService.onPlayerJoin(temp);

        // Extract connection info
        String ip = extractIp(player);
        String vhost = extractVirtualHost(player);

        // Update connection summary + open a new session
        connectionService.updateOnLogin(persistent, ip, vhost);
        sessionService.openSessionOnLogin(persistent, ip, vhost);
    }

    @Subscribe(priority = 10, async = true)
    public void onServerSwitch(ServerConnectedEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        String serverName = event.getServer().getServerInfo().getName();

        playerService.getActivePlayer(uuid).ifPresent(player -> {
            statusService.updateStatus(player, serverName);
            sessionService.updateServerOnSwitch(player, serverName);
        });
    }

    @Subscribe(priority = 10, async = true)
    public void onPlayerQuit(DisconnectEvent event) {
        String username = event.getPlayer().getUsername();
        String uuid = event.getPlayer().getUniqueId().toString();

        Optional<PlayerEntity> activeOpt = playerService.getActivePlayer(uuid);
        activeOpt.ifPresent(statusService::updateStatusOnQuit);
        activeOpt.ifPresent(sessionService::closeSessionOnDisconnect);

        // Remove from active cache last
        playerService.onPlayerQuit(username, uuid);
    }

    private String extractIp(Player player) {
        try {
            SocketAddress sa = player.getRemoteAddress();
            if (sa instanceof InetSocketAddress isa) {
                if (isa.getAddress() != null) {
                    return isa.getAddress().getHostAddress();
                }
                return isa.getHostString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractVirtualHost(Player player) {
        try {
            return player.getVirtualHost()
                    .map(addr -> addr.getHostString() + ":" + addr.getPort())
                    .orElse("");
        } catch (Exception ignored) {
            return "";
        }
    }
}
