package nl.hauntedmc.dataregistry.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.backend.service.PlayerActivitySummaryService;
import nl.hauntedmc.dataregistry.backend.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityInitializationTracker.PlayerIdentityInitialization;
import nl.hauntedmc.dataregistry.backend.service.PlayerNameHistoryService;
import nl.hauntedmc.dataregistry.backend.service.PlayerPlaytimeService;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.backend.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.backend.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.platform.velocity.util.VelocityPlayerAdapter;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serializes Velocity player lifecycle persistence off the proxy event thread.
 * <p>
 * Velocity can emit login, server switch, and disconnect events in quick succession. This listener snapshots the
 * platform state synchronously, then processes all database-backed lifecycle work through a per-player queue so
 * dependent feature tables observe identity creation before later lifecycle updates.
 */
public class PlayerStatusListener {

    private static final int MAX_LOG_VALUE_LENGTH = 256;
    public static final short PLAYER_LIFECYCLE_EVENT_PRIORITY = 1000;

    private final PlayerService playerService;
    private final PlayerNameHistoryService nameHistoryService;
    private final PlayerActivitySummaryService activitySummaryService;
    private final PlayerStatusService statusService;
    private final PlayerConnectionInfoService connectionService;
    private final PlayerSessionService sessionService;
    private final PlayerPlaytimeService playtimeService;
    private final ILoggerAdapter logger;
    private final Executor eventExecutor;
    private final ConcurrentMap<String, CompletableFuture<Void>> playerEventPipelines = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingEvents = new AtomicBoolean(true);

    public PlayerStatusListener(PlayerService playerService,
                                PlayerNameHistoryService nameHistoryService,
                                PlayerActivitySummaryService activitySummaryService,
                                PlayerStatusService statusService,
                                PlayerConnectionInfoService connectionService,
                                PlayerSessionService sessionService,
                                PlayerPlaytimeService playtimeService,
                                ILoggerAdapter logger,
                                Executor eventExecutor) {
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.nameHistoryService = Objects.requireNonNull(nameHistoryService, "nameHistoryService must not be null");
        this.activitySummaryService = Objects.requireNonNull(activitySummaryService, "activitySummaryService must not be null");
        this.statusService = Objects.requireNonNull(statusService, "statusService must not be null");
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService must not be null");
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
        this.playtimeService = Objects.requireNonNull(playtimeService, "playtimeService must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.eventExecutor = Objects.requireNonNull(eventExecutor, "eventExecutor must not be null");
    }

    @Subscribe(priority = PLAYER_LIFECYCLE_EVENT_PRIORITY)
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        String ip = extractIp(player);
        String vhost = extractVirtualHost(player);
        if (!acceptingEvents.get()) {
            return;
        }

        PlayerIdentityInitialization initialization = playerService.beginIdentityInitialization(player.getUniqueId());
        Optional<CompletableFuture<Void>> queuedLogin = enqueuePlayerEvent(uuid, () -> {
            String knownUsername = null;
            PlayerEntity persistent;
            try {
                knownUsername = playerService.findKnownUsername(uuid).orElse(null);
                persistent = playerService.onPlayerJoin(VelocityPlayerAdapter.fromSnapshot(uuid, username));
                playerService.completeIdentityInitialization(initialization, persistent);
            } catch (RuntimeException exception) {
                playerService.failIdentityInitialization(initialization, exception);
                logger.error(
                        "Failed to persist player identity on proxy login for uuid=" + safeForLog(uuid),
                        exception
                );
                return;
            }

            nameHistoryService.recordUsernameChange(persistent, knownUsername, username);
            activitySummaryService.recordLogin(persistent);
            connectionService.updateOnLogin(persistent, ip, vhost);
            sessionService.openSessionOnLogin(persistent, ip, vhost);
        });
        if (queuedLogin.isEmpty()) {
            playerService.completeIdentityInitializationUnavailable(initialization);
            return;
        }
        queuedLogin.get().whenComplete((ignored, failure) -> {
            if (failure != null) {
                playerService.failIdentityInitialization(initialization, failure);
            }
        });
    }

    @Subscribe(priority = PLAYER_LIFECYCLE_EVENT_PRIORITY)
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        String serverName = event.getServer().getServerInfo().getName();

        enqueuePlayerEvent(uuid, () -> {
            PlayerEntity persistent = resolveOrRestorePlayer(uuid, username);
            activitySummaryService.recordSeen(persistent);
            statusService.updateStatus(persistent, serverName);
            sessionService.updateServerOnSwitch(persistent, serverName);
            playtimeService.onServerSwitch(persistent, serverName);
        });
    }

    @Subscribe(priority = PLAYER_LIFECYCLE_EVENT_PRIORITY)
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            playerService.onPlayerQuit(username, uuid);
            return;
        }

        enqueuePlayerEvent(uuid, () -> {
            try {
                PlayerEntity persistent = resolveOrRestorePlayer(uuid, username);
                persistDisconnect(persistent);
            } finally {
                playerService.onPlayerQuit(username, uuid);
            }
        });
    }

    private PlayerEntity resolveOrRestorePlayer(String uuid, String username) {
        Optional<PlayerEntity> activeOpt = playerService.getActivePlayer(uuid);
        return activeOpt.orElseGet(() -> playerService.onPlayerJoin(VelocityPlayerAdapter.fromSnapshot(uuid, username)));
    }

    private Optional<CompletableFuture<Void>> enqueuePlayerEvent(String uuid, Runnable task) {
        return enqueuePlayerEvent(uuid, task, false);
    }

    private Optional<CompletableFuture<Void>> enqueuePlayerEvent(
            String uuid,
            Runnable task,
            boolean allowDuringShutdown
    ) {
        AtomicReference<CompletableFuture<Void>> queuedPipeline = new AtomicReference<>();
        AtomicReference<String> queuedKey = new AtomicReference<>();
        AtomicReference<RuntimeException> schedulingFailure = new AtomicReference<>();
        playerEventPipelines.compute(uuid, (key, currentPipeline) -> {
            if (!allowDuringShutdown && !acceptingEvents.get()) {
                return currentPipeline;
            }
            CompletableFuture<Void> base = currentPipeline == null
                    ? CompletableFuture.completedFuture(null)
                    : currentPipeline.exceptionally(throwable -> null);
            CompletableFuture<Void> next;
            try {
                next = base.thenRunAsync(task, eventExecutor);
            } catch (RuntimeException exception) {
                next = new CompletableFuture<>();
                schedulingFailure.set(exception);
            }
            CompletableFuture<Void> scheduledPipeline = next;
            queuedPipeline.set(scheduledPipeline);
            queuedKey.set(key);
            return scheduledPipeline;
        });

        CompletableFuture<Void> scheduledPipeline = queuedPipeline.get();
        String key = queuedKey.get();
        if (scheduledPipeline != null && key != null) {
            scheduledPipeline.whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    logger.error(
                            "Unhandled exception while processing queued lifecycle event for uuid=" + safeForLog(uuid),
                            throwable
                    );
                }
                playerEventPipelines.remove(key, scheduledPipeline);
            });
            RuntimeException failure = schedulingFailure.get();
            if (failure != null) {
                scheduledPipeline.completeExceptionally(failure);
            }
        }
        return Optional.ofNullable(scheduledPipeline);
    }

    private void persistDisconnect(PlayerEntity persistent) {
        statusService.updateStatusOnQuit(persistent);
        activitySummaryService.recordDisconnect(persistent);
        connectionService.updateOnDisconnect(persistent);
        playtimeService.closeActivePlaytimeOnDisconnect(persistent);
        sessionService.closeSessionOnDisconnect(persistent);
    }

    public void beginShutdown() {
        acceptingEvents.set(false);
    }

    /**
     * Enqueues a lightweight playtime accrual flush for currently active players.
     */
    public void flushActivePlaytime() {
        for (Map.Entry<String, PlayerEntity> entry : playerService.snapshotActivePlayers().entrySet()) {
            PlayerEntity player = entry.getValue();
            if (player == null) {
                continue;
            }
            enqueuePlayerEvent(entry.getKey(), () -> playtimeService.flushActivePlaytime(player));
        }
    }

    /**
     * Enqueues full disconnect persistence for active players that did not emit a disconnect event before shutdown.
     */
    public void closeActivePresenceForShutdown() {
        for (Map.Entry<String, PlayerEntity> entry : playerService.snapshotActivePlayers().entrySet()) {
            PlayerEntity player = entry.getValue();
            if (player == null) {
                continue;
            }
            String uuid = player.getUuid() == null ? entry.getKey() : player.getUuid();
            enqueuePlayerEvent(uuid, () -> {
                try {
                    persistDisconnect(player);
                } finally {
                    playerService.onPlayerQuit(player.getUsername(), uuid);
                }
            }, true);
        }
    }

    public boolean awaitPipelineDrain(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        if (timeout < 0L) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (true) {
            CompletableFuture<?>[] pendingPipelines = playerEventPipelines.values().toArray(CompletableFuture[]::new);
            if (pendingPipelines.length == 0) {
                return true;
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return false;
            }

            try {
                CompletableFuture.allOf(pendingPipelines).get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (ExecutionException ignored) {
                // Individual pipeline failures are logged by enqueuePlayerEvent and still count as completed work.
            } catch (TimeoutException timeoutException) {
                return false;
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static String safeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        int outputLimit = Math.min(value.length(), MAX_LOG_VALUE_LENGTH);
        StringBuilder sanitized = new StringBuilder(outputLimit + 3);
        for (int i = 0; i < value.length() && sanitized.length() < outputLimit; i++) {
            char character = value.charAt(i);
            sanitized.append(Character.isISOControl(character) ? '_' : character);
        }
        if (value.length() > outputLimit) {
            sanitized.append("...");
        }
        return sanitized.toString();
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
