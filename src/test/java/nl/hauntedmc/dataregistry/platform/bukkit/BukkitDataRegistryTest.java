package nl.hauntedmc.dataregistry.platform.bukkit;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.backend.service.ServiceRegistryService;
import nl.hauntedmc.dataregistry.platform.bukkit.logger.BukkitLoggerAdapter;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class BukkitDataRegistryTest {

    @Test
    void startServiceRegistryLifecycleSkipsBackendRegistrationByDefault() throws Exception {
        BukkitDataRegistry plugin = newPluginMock();
        BukkitLoggerAdapter logger = mock(BukkitLoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);

        doReturn(registry).when(plugin).getDataRegistry();

        setField(plugin, "settings", DataRegistrySettings.defaults());
        setField(plugin, "logInstance", logger);

        plugin.startServiceRegistryLifecycle();

        verify(registry, never()).newServiceRegistryService();
        verify(logger).info(
                "Skipping Paper backend service self-registration; Velocity owns backend service identity by default."
        );
        assertNull(getField(plugin, "serviceRegistryService"));
    }

    @Test
    void startServiceRegistryLifecycleRefusesAutoServiceNamesWhenBackendRegistrationEnabled() throws Exception {
        BukkitDataRegistry plugin = newPluginMock();
        BukkitLoggerAdapter logger = mock(BukkitLoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);

        doReturn(registry).when(plugin).getDataRegistry();

        setField(plugin, "settings", DataRegistrySettings.builder()
                .bukkitRegisterServiceInstance(true)
                .build());
        setField(plugin, "logInstance", logger);

        plugin.startServiceRegistryLifecycle();

        verify(registry, never()).newServiceRegistryService();
        verify(logger).warn(
                "platform.bukkit.register-service-instance is enabled but platform.bukkit.service-name is 'auto'. " +
                        "Skipping backend self-registration to avoid duplicate paper-* service identities. " +
                        "Set platform.bukkit.service-name to the matching Velocity server name to enable it."
        );
        assertNull(getField(plugin, "serviceRegistryService"));
    }

    private static BukkitDataRegistry newPluginMock() {
        return mock(
                BukkitDataRegistry.class,
                withSettings().defaultAnswer(invocation -> invocation.callRealMethod())
        );
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = BukkitDataRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = BukkitDataRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
