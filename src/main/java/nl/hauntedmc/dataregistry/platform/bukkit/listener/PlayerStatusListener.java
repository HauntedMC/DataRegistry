package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.bukkit.event.PlayerIdentityReadyEvent;
import nl.hauntedmc.dataregistry.platform.bukkit.util.BukkitPlayerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Maintains Bukkit-side active player identities and signals when they are safe to consume.
 */
public class PlayerStatusListener implements Listener {

    private final PlayerService playerService;
    private final BukkitDataRegistry plugin;
    private final long joinDelayTicks;
    private final Supplier<BukkitScheduler> schedulerSupplier;
    private final Function<UUID, Player> onlinePlayerLookup;
    private final ConcurrentMap<String, Long> playerLifecycleGenerations = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingEvents = new AtomicBoolean(true);

    public PlayerStatusListener(
            BukkitDataRegistry plugin,
            PlayerService playerService,
            int joinDelayTicks
    ) {
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!acceptingEvents.get()) {
            playerService.completeIdentityUnavailable(playerId);
            return;
        }
        long expectedGeneration = markJoinGeneration(playerId.toString());
        playerService.beginIdentityInitialization(playerId);
        processJoinIfStillRelevant(playerId, event.getPlayer().getName(), expectedGeneration);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        long expectedQuitGeneration = markQuitGeneration(uuid);
        scheduleLifecycleGenerationCleanup(uuid, expectedQuitGeneration);
        playerService.completeIdentityUnavailable(event.getPlayer().getUniqueId());
        playerService.onPlayerQuit(
                event.getPlayer().getName(),
                uuid
        );
    }

    /**
     * Completes outstanding identity waiters when DataRegistry is disabled.
     */
    public void shutdown() {
        acceptingEvents.set(false);
        playerLifecycleGenerations.clear();
        playerService.shutdownIdentityReadiness();
    }

    private void processJoinIfStillRelevant(UUID playerId, String usernameSnapshot, long expectedGeneration) {
        String uuid = playerId.toString();
        if (!isGenerationCurrent(uuid, expectedGeneration)) {
            playerService.completeIdentityUnavailable(playerId);
            return;
        }

        Player livePlayer = onlinePlayerLookup.apply(playerId);
        if (livePlayer == null || !livePlayer.isOnline()) {
            playerService.completeIdentityUnavailable(playerId);
            return;
        }

        schedulerSupplier.get().runTaskAsynchronously(plugin, () -> {
            if (!isGenerationCurrent(uuid, expectedGeneration)) {
                return;
            }
            PlayerEntity playerEntity;
            try {
                PlayerEntity temp = BukkitPlayerAdapter.fromSnapshot(uuid, usernameSnapshot);
                playerEntity = playerService.onPlayerJoin(temp);
            } catch (RuntimeException exception) {
                plugin.getPlatformLogger().error("Failed to process Bukkit player join event.", exception);
                if (isGenerationCurrent(uuid, expectedGeneration)) {
                    playerService.failIdentityInitialization(playerId, exception);
                }
                return;
            }

            Long currentGeneration = playerLifecycleGenerations.get(uuid);
            if (currentGeneration != null && currentGeneration > expectedGeneration && currentGeneration % 2L == 0L) {
                playerService.onPlayerQuit(usernameSnapshot, uuid);
                playerService.completeIdentityUnavailable(playerId);
                return;
            }
            if (!isGenerationCurrent(uuid, expectedGeneration)) {
                return;
            }
            playerService.completeIdentityReady(playerEntity);
            fireIdentityReadyEventIfOnline(playerId, playerEntity, expectedGeneration);
        });
    }

    private void fireIdentityReadyEventIfOnline(UUID playerId, PlayerEntity playerEntity, long expectedGeneration) {
        try {
            schedulerSupplier.get().runTask(plugin, () -> {
                Player livePlayer = onlinePlayerLookup.apply(playerId);
                if (!acceptingEvents.get()
                        || !isGenerationCurrent(playerId.toString(), expectedGeneration)
                        || livePlayer == null
                        || !livePlayer.isOnline()) {
                    return;
                }
                Bukkit.getPluginManager().callEvent(new PlayerIdentityReadyEvent(
                        new nl.hauntedmc.dataregistry.api.player.PlayerIdentity(
                                playerEntity.getId(),
                                playerId,
                                playerEntity.getUsername()
                        )
                ));
            });
        } catch (RuntimeException exception) {
            plugin.getPlatformLogger().warn("Failed to dispatch player identity ready event.", exception);
        }
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

    private void scheduleLifecycleGenerationCleanup(String uuid, long expectedGeneration) {
        long cleanupDelay = Math.max(1L, joinDelayTicks + 1L);
        schedulerSupplier.get().runTaskLater(
                plugin,
                () -> playerLifecycleGenerations.compute(
                        uuid,
                        (key, currentGeneration) ->
                                shouldDropLifecycleGeneration(currentGeneration, expectedGeneration) ? null : currentGeneration
                ),
                cleanupDelay
        );
    }

    private static boolean shouldDropLifecycleGeneration(Long currentGeneration, long expectedGeneration) {
        return currentGeneration != null
                && currentGeneration == expectedGeneration
                && currentGeneration % 2L == 0L;
    }

    private boolean isGenerationCurrent(String uuid, long expectedGeneration) {
        return playerLifecycleGenerations.getOrDefault(uuid, 0L) == expectedGeneration;
    }
}
