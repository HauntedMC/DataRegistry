package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerIdentityInitializationTracker.PlayerIdentityInitialization;
import nl.hauntedmc.dataregistry.core.service.PlayerService;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Maintains Bukkit-side active player identities and signals when they are safe to consume.
 */
public class PlayerStatusListener implements Listener {

    private static final long QUIT_GENERATION_CLEANUP_TICKS = 1L;

    private final PlayerService playerService;
    private final BukkitDataRegistry plugin;
    private final long joinDelayTicks;
    private final Supplier<BukkitScheduler> schedulerSupplier;
    private final Function<UUID, Player> onlinePlayerLookup;
    private final ConcurrentMap<String, Long> playerLifecycleGenerations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PlayerIdentityInitialization> playerIdentityInitializations =
            new ConcurrentHashMap<>();
    private final AtomicLong lifecycleGenerationSequence = new AtomicLong();
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
            return;
        }
        String uuid = playerId.toString();
        long expectedGeneration = markJoinGeneration(uuid);
        PlayerIdentityInitialization initialization = playerService.beginIdentityInitialization(playerId);
        PlayerIdentityInitialization previousInitialization = playerIdentityInitializations.put(uuid, initialization);
        playerService.completeIdentityInitializationUnavailable(previousInitialization);
        scheduleJoinProcessing(playerId, expectedGeneration, initialization);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        long expectedQuitGeneration = markQuitGeneration(uuid);
        scheduleLifecycleGenerationCleanup(uuid, expectedQuitGeneration);
        playerService.completeIdentityInitializationUnavailable(playerIdentityInitializations.remove(uuid));
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
        playerIdentityInitializations.clear();
        playerService.shutdownIdentityInitialization();
    }

    private void processJoinIfStillRelevant(
            UUID playerId,
            long expectedGeneration,
            PlayerIdentityInitialization initialization
    ) {
        String uuid = playerId.toString();
        if (!isGenerationCurrent(uuid, expectedGeneration)) {
            completeUnavailable(uuid, initialization);
            return;
        }

        Player livePlayer = onlinePlayerLookup.apply(playerId);
        if (livePlayer == null || !livePlayer.isOnline()) {
            completeUnavailable(uuid, initialization);
            return;
        }
        String usernameSnapshot = livePlayer.getName();

        try {
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
                        failInitialization(uuid, initialization, exception);
                    }
                    return;
                }

                Long currentGeneration = playerLifecycleGenerations.get(uuid);
                if (!isGenerationCurrent(uuid, expectedGeneration)) {
                    // A quit can arrive while the persistence work is in flight. Do not close a newer reconnect,
                    // but undo this stale join when there is no newer active generation.
                    if (currentGeneration == null
                            || (currentGeneration > expectedGeneration && currentGeneration % 2L == 0L)) {
                        playerService.onPlayerQuit(usernameSnapshot, uuid);
                    }
                    completeUnavailable(uuid, initialization);
                    return;
                }
                playerIdentityInitializations.remove(uuid, initialization);
                playerService.completeIdentityInitialization(initialization, playerEntity);
                fireIdentityReadyEventIfOnline(playerId, playerEntity, expectedGeneration);
            });
        } catch (RuntimeException exception) {
            plugin.getPlatformLogger().warn("Failed to schedule Bukkit player join processing.", exception);
            failInitialization(uuid, initialization, exception);
        }
    }

    private void completeUnavailable(String uuid, PlayerIdentityInitialization initialization) {
        playerIdentityInitializations.remove(uuid, initialization);
        playerService.completeIdentityInitializationUnavailable(initialization);
    }

    private void failInitialization(
            String uuid,
            PlayerIdentityInitialization initialization,
            RuntimeException exception
    ) {
        playerIdentityInitializations.remove(uuid, initialization);
        playerService.failIdentityInitialization(initialization, exception);
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
        long generation = nextGeneration(true);
        playerLifecycleGenerations.put(uuid, generation);
        return generation;
    }

    private long markQuitGeneration(String uuid) {
        long generation = nextGeneration(false);
        playerLifecycleGenerations.put(uuid, generation);
        return generation;
    }

    private long nextGeneration(boolean join) {
        return lifecycleGenerationSequence.updateAndGet(currentGeneration -> {
            long nextGeneration = currentGeneration + 1L;
            boolean isJoinGeneration = nextGeneration % 2L == 1L;
            return isJoinGeneration == join ? nextGeneration : nextGeneration + 1L;
        });
    }

    private void scheduleJoinProcessing(
            UUID playerId,
            long expectedGeneration,
            PlayerIdentityInitialization initialization
    ) {
        if (joinDelayTicks == 0L) {
            processJoinIfStillRelevant(playerId, expectedGeneration, initialization);
            return;
        }
        try {
            schedulerSupplier.get().runTaskLater(
                    plugin,
                    () -> processJoinIfStillRelevant(playerId, expectedGeneration, initialization),
                    joinDelayTicks
            );
        } catch (RuntimeException exception) {
            plugin.getPlatformLogger().warn("Failed to schedule delayed Bukkit player join processing.", exception);
            failInitialization(playerId.toString(), initialization, exception);
        }
    }

    private void scheduleLifecycleGenerationCleanup(String uuid, long expectedGeneration) {
        try {
            schedulerSupplier.get().runTaskLater(
                    plugin,
                    () -> cleanupLifecycleGeneration(uuid, expectedGeneration),
                    QUIT_GENERATION_CLEANUP_TICKS
            );
        } catch (RuntimeException exception) {
            plugin.getPlatformLogger().warn("Failed to schedule Bukkit player lifecycle cleanup.", exception);
            cleanupLifecycleGeneration(uuid, expectedGeneration);
        }
    }

    private void cleanupLifecycleGeneration(String uuid, long expectedGeneration) {
        playerLifecycleGenerations.compute(
                uuid,
                (key, currentGeneration) ->
                        shouldDropLifecycleGeneration(currentGeneration, expectedGeneration) ? null : currentGeneration
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
