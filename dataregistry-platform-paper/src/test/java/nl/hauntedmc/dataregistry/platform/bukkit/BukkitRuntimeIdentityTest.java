package nl.hauntedmc.dataregistry.platform.bukkit;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.runtime.RuntimeKind;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class BukkitRuntimeIdentityTest {

    @Test
    void explicitBukkitServiceNamePublishesBackendIdentityWhenRuntimeIsActive() throws Exception {
        BukkitDataRegistry plugin = activePlugin();
        setSettings(plugin, DataRegistrySettings.builder().bukkitServiceName(" lobby-02 ").build());

        var identity = plugin.getRuntimeIdentity().orElseThrow();

        assertEquals("lobby-02", identity.serviceName());
        assertEquals(RuntimeKind.BACKEND, identity.kind());
    }

    @Test
    void autoBukkitServiceNameNeverPublishesInventedIdentity() throws Exception {
        BukkitDataRegistry plugin = activePlugin();
        setSettings(plugin, DataRegistrySettings.defaults());

        assertTrue(plugin.getRuntimeIdentity().isEmpty());
    }

    @Test
    void inactivePaperRuntimePublishesNoIdentity() {
        BukkitDataRegistry plugin = mock(
                BukkitDataRegistry.class,
                withSettings().defaultAnswer(invocation -> invocation.callRealMethod())
        );
        doThrow(new IllegalStateException("runtime unavailable")).when(plugin).getDataRegistry();

        assertTrue(plugin.getRuntimeIdentity().isEmpty());
    }

    private static BukkitDataRegistry activePlugin() {
        BukkitDataRegistry plugin = mock(
                BukkitDataRegistry.class,
                withSettings().defaultAnswer(invocation -> invocation.callRealMethod())
        );
        doReturn(mock(DataRegistryApi.class)).when(plugin).getDataRegistry();
        return plugin;
    }

    private static void setSettings(BukkitDataRegistry plugin, DataRegistrySettings settings) throws Exception {
        Field field = BukkitDataRegistry.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(plugin, settings);
    }
}
