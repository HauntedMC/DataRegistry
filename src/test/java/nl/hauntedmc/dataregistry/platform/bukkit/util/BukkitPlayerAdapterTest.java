package nl.hauntedmc.dataregistry.platform.bukkit.util;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitPlayerAdapterTest {

    @Test
    void fromPlatformPlayerRejectsNull() {
        assertThrows(NullPointerException.class, () -> BukkitPlayerAdapter.fromPlatformPlayer(null));
    }

    @Test
    void fromPlatformPlayerMapsUuidAndUsername() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alice");

        PlayerEntity entity = BukkitPlayerAdapter.fromPlatformPlayer(player);

        assertEquals(uuid.toString(), entity.getUuid());
        assertEquals("Alice", entity.getUsername());
    }

    @Test
    void fromSnapshotMapsValuesAndRejectsNulls() {
        assertThrows(NullPointerException.class, () -> BukkitPlayerAdapter.fromSnapshot(null, "Alice"));
        assertThrows(NullPointerException.class, () -> BukkitPlayerAdapter.fromSnapshot(UUID.randomUUID().toString(), null));

        UUID uuid = UUID.randomUUID();
        PlayerEntity entity = BukkitPlayerAdapter.fromSnapshot(uuid.toString(), "Alice");
        assertEquals(uuid.toString(), entity.getUuid());
        assertEquals("Alice", entity.getUsername());
    }

    @Test
    void toPlatformPlayerReturnsNullForNullOrInvalidEntityState() {
        assertNull(BukkitPlayerAdapter.toPlatformPlayer(null));

        PlayerEntity noUuid = new PlayerEntity();
        assertNull(BukkitPlayerAdapter.toPlatformPlayer(noUuid));

        PlayerEntity invalid = new PlayerEntity();
        invalid.setUuid("invalid");
        assertNull(BukkitPlayerAdapter.toPlatformPlayer(invalid));
    }
}
