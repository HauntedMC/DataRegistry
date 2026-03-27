package nl.hauntedmc.dataregistry.platform.velocity.util;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VelocityPlayerAdapterTest {

    @AfterEach
    void resetProxy() throws Exception {
        setProxyField(null);
    }

    @Test
    void setProxyRejectsNull() {
        assertThrows(NullPointerException.class, () -> VelocityPlayerAdapter.setProxy(null));
    }

    @Test
    void fromPlatformPlayerRejectsNull() {
        assertThrows(NullPointerException.class, () -> VelocityPlayerAdapter.fromPlatformPlayer(null));
    }

    @Test
    void fromPlatformPlayerMapsUuidAndUsername() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("Alice");

        PlayerEntity entity = VelocityPlayerAdapter.fromPlatformPlayer(player);

        assertEquals(uuid.toString(), entity.getUuid());
        assertEquals("Alice", entity.getUsername());
    }

    @Test
    void fromSnapshotMapsValuesAndRejectsNulls() {
        assertThrows(NullPointerException.class, () -> VelocityPlayerAdapter.fromSnapshot(null, "Alice"));
        assertThrows(NullPointerException.class, () -> VelocityPlayerAdapter.fromSnapshot(UUID.randomUUID().toString(), null));

        UUID uuid = UUID.randomUUID();
        PlayerEntity entity = VelocityPlayerAdapter.fromSnapshot(uuid.toString(), "Alice");
        assertEquals(uuid.toString(), entity.getUuid());
        assertEquals("Alice", entity.getUsername());
    }

    @Test
    void toPlatformPlayerHandlesNullEntityNullUuidAndInvalidUuid() throws Exception {
        ProxyServer proxyServer = mock(ProxyServer.class);
        setProxyField(proxyServer);

        assertNull(VelocityPlayerAdapter.toPlatformPlayer(null));

        PlayerEntity noUuid = new PlayerEntity();
        assertNull(VelocityPlayerAdapter.toPlatformPlayer(noUuid));

        PlayerEntity invalidUuid = new PlayerEntity();
        invalidUuid.setUuid("invalid");
        assertNull(VelocityPlayerAdapter.toPlatformPlayer(invalidUuid));
    }

    @Test
    void toPlatformPlayerThrowsWhenProxyIsNotConfigured() throws Exception {
        setProxyField(null);
        PlayerEntity entity = new PlayerEntity();
        entity.setUuid(UUID.randomUUID().toString());

        assertThrows(IllegalStateException.class, () -> VelocityPlayerAdapter.toPlatformPlayer(entity));
    }

    @Test
    void toPlatformPlayerResolvesOnlinePlayerThroughConfiguredProxy() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        VelocityPlayerAdapter.setProxy(proxyServer);
        PlayerEntity entity = new PlayerEntity();
        UUID uuid = UUID.randomUUID();
        entity.setUuid(uuid.toString());
        Player player = mock(Player.class);

        when(proxyServer.getPlayer(uuid)).thenReturn(Optional.of(player));
        assertSame(player, VelocityPlayerAdapter.toPlatformPlayer(entity));

        when(proxyServer.getPlayer(uuid)).thenReturn(Optional.empty());
        assertNull(VelocityPlayerAdapter.toPlatformPlayer(entity));
    }

    private static void setProxyField(ProxyServer proxyServer) throws Exception {
        Field field = VelocityPlayerAdapter.class.getDeclaredField("proxy");
        field.setAccessible(true);
        field.set(null, proxyServer);
    }
}
