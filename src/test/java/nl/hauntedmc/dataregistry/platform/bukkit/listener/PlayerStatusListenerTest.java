package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityReadiness;
import nl.hauntedmc.dataregistry.backend.player.DefaultPlayerDirectory;
import nl.hauntedmc.dataregistry.backend.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStatusListenerTest {

    @Test
    void bukkitLifecycleHandlersStartAtLowestPriority() throws Exception {
        assertLifecyclePriority("onPlayerJoin", PlayerJoinEvent.class);
        assertLifecyclePriority("onPlayerQuit", PlayerQuitEvent.class);
    }

    @Test
    void constructorRejectsNullDependencies() {
        PlayerService playerService = new PlayerService(
                mock(PlayerRepository.class),
                new PlayerIdentityReadiness(),
                mock(ILoggerAdapter.class)
        );
        BukkitDataRegistry plugin = mock(BukkitDataRegistry.class);

        assertThrows(NullPointerException.class, () -> new PlayerStatusListener(null, playerService, 0));
        assertThrows(NullPointerException.class, () -> new PlayerStatusListener(plugin, null, 0));
    }

    private static void assertLifecyclePriority(String methodName, Class<?> eventType) throws Exception {
        Method method = PlayerStatusListener.class.getDeclaredMethod(methodName, eventType);
        EventHandler eventHandler = method.getAnnotation(EventHandler.class);
        assertEquals(EventPriority.LOWEST, eventHandler.priority());
    }

    @Test
    void onPlayerQuitRemovesCachedPlayerEntry() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        PlayerService playerService = new PlayerService(repository, readiness, logger);
        BukkitDataRegistry plugin = mock(BukkitDataRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PlayerStatusListener listener = new PlayerStatusListener(
                plugin,
                playerService,
                4,
                () -> scheduler,
                id -> null
        );
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alice");

        listener.onPlayerQuit(new PlayerQuitEvent(player, "quit"));

        verify(repository).removeActivePlayer(uuid.toString());
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(5L));
    }

    @Test
    void onPlayerJoinSchedulesAsyncPersistenceWhenPlayerStillOnline() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        PlayerService playerService = new PlayerService(repository, readiness, logger);
        BukkitDataRegistry plugin = mock(BukkitDataRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Player joinedPlayer = mock(Player.class);
        Player livePlayer = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        PlayerStatusListener listener = new PlayerStatusListener(
                plugin,
                playerService,
                4,
                () -> scheduler,
                id -> id.equals(uuid) ? livePlayer : null
        );
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(joinedPlayer, "join");
        PlayerEntity persisted = new PlayerEntity();
        persisted.setId(1L);
        persisted.setUuid(uuid.toString());
        persisted.setUsername("Alice");

        when(joinedPlayer.getUniqueId()).thenReturn(uuid);
        when(joinedPlayer.getName()).thenReturn("Alice");
        when(livePlayer.isOnline()).thenReturn(true);
        when(livePlayer.getName()).thenReturn("Alice");
        when(repository.getOrCreateActivePlayer(uuid.toString(), "Alice")).thenReturn(persisted);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        });

        listener.onPlayerJoin(joinEvent);

        verify(repository).getOrCreateActivePlayer(uuid.toString(), "Alice");
        verify(scheduler, never()).runTaskLater(eq(plugin), any(Runnable.class), eq(4L));
    }

    @Test
    void onPlayerJoinSkipsPersistenceWhenPlayerAlreadyOffline() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        PlayerService playerService = new PlayerService(repository, readiness, logger);
        BukkitDataRegistry plugin = mock(BukkitDataRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Player joinedPlayer = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        PlayerStatusListener listener = new PlayerStatusListener(
                plugin,
                playerService,
                4,
                () -> scheduler,
                id -> null
        );
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(joinedPlayer, "join");

        when(joinedPlayer.getUniqueId()).thenReturn(uuid);
        when(joinedPlayer.getName()).thenReturn("Alice");

        listener.onPlayerJoin(joinEvent);

        verify(scheduler, never()).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        verify(repository, never()).getOrCreateActivePlayer(any(), any());
    }

    @Test
    void onPlayerQuitCancelsPendingDelayedJoinGeneration() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        PlayerService playerService = new PlayerService(repository, readiness, logger);
        BukkitDataRegistry plugin = mock(BukkitDataRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Player joinedPlayer = mock(Player.class);
        Player quitPlayer = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        PlayerStatusListener listener = new PlayerStatusListener(
                plugin,
                playerService,
                4,
                () -> scheduler,
                id -> null
        );
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(joinedPlayer, "join");

        when(joinedPlayer.getUniqueId()).thenReturn(uuid);
        when(joinedPlayer.getName()).thenReturn("Alice");
        when(quitPlayer.getUniqueId()).thenReturn(uuid);
        when(quitPlayer.getName()).thenReturn("Alice");

        listener.onPlayerJoin(joinEvent);
        listener.onPlayerQuit(new PlayerQuitEvent(quitPlayer, "quit"));

        verify(scheduler, never()).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        verify(repository).removeActivePlayer(uuid.toString());
    }

    @Test
    void staleJoinTaskDoesNotCompleteReconnectReadiness() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerIdentityReadiness readiness = new PlayerIdentityReadiness();
        PlayerService playerService = new PlayerService(repository, readiness, logger);
        PlayerDirectory playerDirectory = new DefaultPlayerDirectory(repository, readiness);
        BukkitDataRegistry plugin = mock(BukkitDataRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Player firstJoinPlayer = mock(Player.class);
        Player quitPlayer = mock(Player.class);
        Player secondJoinPlayer = mock(Player.class);
        Player livePlayer = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        List<Runnable> asyncTasks = new ArrayList<>();
        AtomicReference<Player> onlinePlayer = new AtomicReference<>(livePlayer);
        PlayerStatusListener listener = new PlayerStatusListener(
                plugin,
                playerService,
                4,
                () -> scheduler,
                id -> id.equals(uuid) ? onlinePlayer.get() : null
        );
        PlayerEntity persisted = new PlayerEntity();
        persisted.setId(1L);
        persisted.setUuid(uuid.toString());
        persisted.setUsername("Alice");

        when(firstJoinPlayer.getUniqueId()).thenReturn(uuid);
        when(firstJoinPlayer.getName()).thenReturn("Alice");
        when(quitPlayer.getUniqueId()).thenReturn(uuid);
        when(quitPlayer.getName()).thenReturn("Alice");
        when(secondJoinPlayer.getUniqueId()).thenReturn(uuid);
        when(secondJoinPlayer.getName()).thenReturn("Alice");
        when(livePlayer.isOnline()).thenReturn(true);
        when(repository.getOrCreateActivePlayer(uuid.toString(), "Alice")).thenReturn(persisted);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            asyncTasks.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });

        listener.onPlayerJoin(new PlayerJoinEvent(firstJoinPlayer, "join"));
        listener.onPlayerQuit(new PlayerQuitEvent(quitPlayer, "quit"));
        listener.onPlayerJoin(new PlayerJoinEvent(secondJoinPlayer, "join"));

        CompletableFuture<?> secondJoinReadiness = playerDirectory.whenReady(uuid);
        asyncTasks.get(0).run();

        assertFalse(secondJoinReadiness.isDone());
    }
}
