package nl.hauntedmc.dataregistry.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.backend.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.backend.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.backend.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.velocity.util.VelocityPlayerAdapter;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
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
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.statusService = Objects.requireNonNull(statusService, "statusService must not be null");
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService must not be null");
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
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
        activeOpt.ifPresent(connectionService::updateOnDisconnect);
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
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String extractVirtualHost(Player player) {
        try {
            return player.getVirtualHost()
                    .map(addr -> addr.getHostString() + ":" + addr.getPort())
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}
