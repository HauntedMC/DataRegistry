package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.bukkit.util.BukkitPlayerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

public class PlayerStatusListener implements Listener {

    private final PlayerService playerService;
    private final BukkitDataRegistry plugin;
    private final long joinDelayTicks;
    private final Supplier<BukkitScheduler> schedulerSupplier;
    private final Function<UUID, Player> onlinePlayerLookup;
    private final ConcurrentMap<String, Long> playerLifecycleGenerations = new ConcurrentHashMap<>();

    public PlayerStatusListener(BukkitDataRegistry plugin, PlayerService playerService, int joinDelayTicks) {
        this(plugin, playerService, joinDelayTicks, Bukkit::getScheduler, Bukkit::getPlayer);
    }

    PlayerStatusListener(
            BukkitDataRegistry plugin,
            PlayerService playerService,
            int joinDelayTicks,
            Supplier<BukkitScheduler> schedulerSupplier,
            Function<UUID, Player> onlinePlayerLookup
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.joinDelayTicks = joinDelayTicks;
        this.schedulerSupplier = Objects.requireNonNull(schedulerSupplier, "schedulerSupplier must not be null");
        this.onlinePlayerLookup = Objects.requireNonNull(onlinePlayerLookup, "onlinePlayerLookup must not be null");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        long expectedGeneration = markJoinGeneration(playerId.toString());
        long delay = Math.max(0L, joinDelayTicks);
        schedulerSupplier.get().runTaskLater(
                plugin,
                () -> processJoinIfStillRelevant(playerId, expectedGeneration),
                delay
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        markQuitGeneration(event.getPlayer().getUniqueId().toString());
        playerService.onPlayerQuit(
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId().toString()
        );
    }

    private void processJoinIfStillRelevant(UUID playerId, long expectedGeneration) {
        String uuid = playerId.toString();
        if (!isGenerationCurrent(uuid, expectedGeneration)) {
            return;
        }

        Player livePlayer = onlinePlayerLookup.apply(playerId);
        if (livePlayer == null || !livePlayer.isOnline()) {
            return;
        }

        String usernameSnapshot = livePlayer.getName();
        schedulerSupplier.get().runTaskAsynchronously(plugin, () -> {
            if (!isGenerationCurrent(uuid, expectedGeneration)) {
                return;
            }
            try {
                PlayerEntity temp = BukkitPlayerAdapter.fromSnapshot(uuid, usernameSnapshot);
                playerService.onPlayerJoin(temp);
            } catch (RuntimeException exception) {
                plugin.getPlatformLogger().error("Failed to process Bukkit player join event.", exception);
                return;
            }

            Long currentGeneration = playerLifecycleGenerations.get(uuid);
            if (currentGeneration != null && currentGeneration > expectedGeneration && currentGeneration % 2L == 0L) {
                playerService.onPlayerQuit(usernameSnapshot, uuid);
            }
        });
    }

    private long markJoinGeneration(String uuid) {
        return playerLifecycleGenerations.compute(uuid, (key, currentGeneration) -> {
            if (currentGeneration == null) {
                return 1L;
            }
            return currentGeneration % 2L == 0L ? currentGeneration + 1L : currentGeneration + 2L;
        });
    }

    private long markQuitGeneration(String uuid) {
        return playerLifecycleGenerations.compute(uuid, (key, currentGeneration) -> {
            if (currentGeneration == null) {
                return 2L;
            }
            return currentGeneration % 2L == 1L ? currentGeneration + 1L : currentGeneration + 2L;
        });
    }

    private boolean isGenerationCurrent(String uuid, long expectedGeneration) {
        return playerLifecycleGenerations.getOrDefault(uuid, 0L) == expectedGeneration;
    }
}
