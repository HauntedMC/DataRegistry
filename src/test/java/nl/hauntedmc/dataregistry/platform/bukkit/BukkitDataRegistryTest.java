package nl.hauntedmc.dataregistry.platform.bukkit;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.backend.service.ServiceRegistryService;
import nl.hauntedmc.dataregistry.platform.bukkit.logger.BukkitLoggerAdapter;
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
        ServiceRegistryService registryService = mock(ServiceRegistryService.class);

        when(plugin.getDataRegistry()).thenReturn(registry);
        when(registry.newServiceRegistryService()).thenReturn(registryService);

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
        ServiceRegistryService registryService = mock(ServiceRegistryService.class);

        when(plugin.getDataRegistry()).thenReturn(registry);
        when(registry.newServiceRegistryService()).thenReturn(registryService);

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

    @Test
    void startServiceRegistryLifecycleRegistersExplicitBackendServiceName() throws Exception {
        BukkitDataRegistry plugin = newPluginMock();
        BukkitLoggerAdapter logger = mock(BukkitLoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);
        ServiceRegistryService registryService = mock(ServiceRegistryService.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        when(plugin.getDataRegistry()).thenReturn(registry);
        when(plugin.getServer()).thenReturn(server);
        when(registry.newServiceRegistryService()).thenReturn(registryService);
        when(server.getIp()).thenReturn("10.0.0.5");
        when(server.getPort()).thenReturn(25565);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

        setField(plugin, "settings", DataRegistrySettings.builder()
                .bukkitRegisterServiceInstance(true)
                .bukkitServiceName("lobby")
                .serviceHeartbeatIntervalSeconds(30)
                .build());
        setField(plugin, "logInstance", logger);

        plugin.startServiceRegistryLifecycle();

        verify(registry).newServiceRegistryService();
        verify(registryService).refreshRunningInstance(
                eq(ServiceKind.BACKEND),
                eq("lobby"),
                eq("PAPER"),
                any(String.class),
                eq("10.0.0.5"),
                eq(25565)
        );
        verify(scheduler).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(600L), eq(600L));
        assertNotNull(getField(plugin, "serviceRegistryService"));
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
