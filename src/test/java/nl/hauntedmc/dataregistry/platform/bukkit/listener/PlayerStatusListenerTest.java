package nl.hauntedmc.dataregistry.platform.bukkit.listener;

import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.bukkit.BukkitDataRegistry;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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
        PlayerStatusListener listener = new PlayerStatusListener(plugin, playerService, 4);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alice");

        listener.onPlayerQuit(new PlayerQuitEvent(player, "quit"));

        verify(repository).removeActivePlayer(uuid.toString());
    }
}
