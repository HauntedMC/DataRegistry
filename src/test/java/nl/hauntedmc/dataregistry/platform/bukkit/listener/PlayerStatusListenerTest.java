package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStatusListenerTest {

    @Test
    void constructorRejectsNullDependencies() {
        PlayerService playerService = new PlayerService(
                mock(PlayerRepository.class),
                mock(ILoggerAdapter.class)
        );
        BukkitDataRegistry plugin = mock(BukkitDataRegistry.class);

        assertThrows(NullPointerException.class, () -> new PlayerStatusListener(null, playerService, 0));
        assertThrows(NullPointerException.class, () -> new PlayerStatusListener(plugin, null, 0));
    }

    @Test
    void onPlayerQuitRemovesCachedPlayerEntry() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService playerService = new PlayerService(repository, logger);
        BukkitDataRegistry plugin = mock(BukkitDataRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PlayerStatusListener listener = new PlayerStatusListener(plugin, playerService, 4, () -> scheduler, id -> null);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alice");

        listener.onPlayerQuit(new PlayerQuitEvent(player, "quit"));

        verify(repository).removeActivePlayer(uuid.toString());
    }

    @Test
    void onPlayerJoinSchedulesAsyncPersistenceWhenPlayerStillOnline() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService playerService = new PlayerService(repository, logger);
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
        when(livePlayer.isOnline()).thenReturn(true);
        when(livePlayer.getName()).thenReturn("Alice");
        when(repository.getOrCreateActivePlayer(uuid.toString(), "Alice")).thenReturn(persisted);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(4L))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        });

        listener.onPlayerJoin(joinEvent);

        verify(repository).getOrCreateActivePlayer(uuid.toString(), "Alice");
    }

    @Test
    void onPlayerJoinSkipsPersistenceWhenPlayerAlreadyOffline() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService playerService = new PlayerService(repository, logger);
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
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(4L))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        });

        listener.onPlayerJoin(joinEvent);

        verify(scheduler, never()).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        verify(repository, never()).getOrCreateActivePlayer(any(), any());
    }

    @Test
    void onPlayerQuitCancelsPendingDelayedJoinGeneration() {
        PlayerRepository repository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerService playerService = new PlayerService(repository, logger);
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
        AtomicReference<Runnable> delayedTask = new AtomicReference<>();

        when(joinedPlayer.getUniqueId()).thenReturn(uuid);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(4L))).thenAnswer(invocation -> {
            delayedTask.set(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(quitPlayer.getUniqueId()).thenReturn(uuid);
        when(quitPlayer.getName()).thenReturn("Alice");

        listener.onPlayerJoin(joinEvent);
        listener.onPlayerQuit(new PlayerQuitEvent(quitPlayer, "quit"));
        delayedTask.get().run();

        verify(scheduler, never()).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        verify(repository).removeActivePlayer(uuid.toString());
    }
}
