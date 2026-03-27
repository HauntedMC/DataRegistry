package nl.hauntedmc.dataregistry.platform.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderApiSupplier;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.platform.velocity.listener.PlayerStatusListener;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityDataRegistryTest {
    private static final Path TEST_DATA_DIRECTORY = Path.of("target", "test-data", "velocity-dataregistry");

    @Test
    void lifecycleHandlersUseDeterministicVelocityEventOrder() throws ReflectiveOperationException {
        Method initializeHandler = VelocityDataRegistry.class.getDeclaredMethod(
                "onProxyInitialize",
                ProxyInitializeEvent.class
        );
        Subscribe initializeSubscribe = initializeHandler.getAnnotation(Subscribe.class);
        assertNotNull(initializeSubscribe);
        assertEquals(VelocityDataRegistry.INITIALIZE_EVENT_PRIORITY, initializeSubscribe.priority());

        Method shutdownHandler = VelocityDataRegistry.class.getDeclaredMethod(
                "onProxyShutdown",
                ProxyShutdownEvent.class
        );
        Subscribe shutdownSubscribe = shutdownHandler.getAnnotation(Subscribe.class);
        assertNotNull(shutdownSubscribe);
        assertEquals(VelocityDataRegistry.SHUTDOWN_EVENT_PRIORITY, shutdownSubscribe.priority());
    }

    @Test
    void resolvePluginVersionReturnsDescriptionVersionValue() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        PluginContainer pluginContainer = mock(PluginContainer.class);
        PluginDescription pluginDescription = mock(PluginDescription.class);
        Object pluginInstance = new Object();

        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.fromInstance(pluginInstance)).thenReturn(Optional.of(pluginContainer));
        when(pluginContainer.getDescription()).thenReturn(pluginDescription);
        when(pluginDescription.getVersion()).thenReturn(Optional.of("1.6.0"));

        assertEquals("1.6.0", VelocityDataRegistry.resolvePluginVersion(proxyServer, pluginInstance));
    }

    @Test
    void resolvePluginVersionFallsBackToUnknownWhenVersionMissing() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        PluginContainer pluginContainer = mock(PluginContainer.class);
        PluginDescription pluginDescription = mock(PluginDescription.class);
        Object pluginInstance = new Object();

        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.fromInstance(pluginInstance)).thenReturn(Optional.of(pluginContainer));
        when(pluginContainer.getDescription()).thenReturn(pluginDescription);
        when(pluginDescription.getVersion()).thenReturn(Optional.empty());

        assertEquals("unknown", VelocityDataRegistry.resolvePluginVersion(proxyServer, pluginInstance));
    }

    @Test
    void resolveDataProviderApiReturnsSupplierApi() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        PluginContainer pluginContainer = mock(PluginContainer.class);
        Logger logger = mock(Logger.class);
        DataProviderApiSupplier supplier = mock(DataProviderApiSupplier.class);
        DataProviderAPI api = mock(DataProviderAPI.class);

        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("dataprovider")).thenReturn(Optional.of(pluginContainer));
        doReturn(Optional.of(supplier)).when(pluginContainer).getInstance();
        when(supplier.dataProviderApi()).thenReturn(api);

        VelocityDataRegistry plugin = new VelocityDataRegistry(proxyServer, logger, TEST_DATA_DIRECTORY);

        assertSame(api, plugin.resolveDataProviderApi());
    }

    @Test
    void resolveDataProviderApiReturnsNullWhenSupplierThrows() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        PluginContainer pluginContainer = mock(PluginContainer.class);
        Logger logger = mock(Logger.class);
        DataProviderApiSupplier supplier = mock(DataProviderApiSupplier.class);

        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("dataprovider")).thenReturn(Optional.of(pluginContainer));
        doReturn(Optional.of(supplier)).when(pluginContainer).getInstance();
        when(supplier.dataProviderApi()).thenThrow(new IllegalStateException("boom"));

        VelocityDataRegistry plugin = new VelocityDataRegistry(proxyServer, logger, TEST_DATA_DIRECTORY);

        assertNull(plugin.resolveDataProviderApi());
        verify(logger, atLeastOnce()).error(anyString(), any(Throwable.class));
    }

    @Test
    void resolveDataProviderApiReturnsNullWhenPluginIsMissingOrInvalid() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        PluginContainer pluginContainer = mock(PluginContainer.class);
        Logger logger = mock(Logger.class);

        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("dataprovider")).thenReturn(Optional.empty(), Optional.of(pluginContainer));
        doReturn(Optional.of(new Object())).when(pluginContainer).getInstance();

        VelocityDataRegistry plugin = new VelocityDataRegistry(proxyServer, logger, TEST_DATA_DIRECTORY);
        assertNull(plugin.resolveDataProviderApi());
        assertNull(plugin.resolveDataProviderApi());
        verify(logger, times(2)).error("Failed to resolve DataProvider API supplier from plugin container.");
    }

    @Test
    void getPlatformLoggerIsLazilyInitializedAndReused() {
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                mock(ProxyServer.class),
                mock(Logger.class),
                TEST_DATA_DIRECTORY
        );

        ILoggerAdapter logger1 = plugin.getPlatformLogger();
        ILoggerAdapter logger2 = plugin.getPlatformLogger();

        assertNotNull(logger1);
        assertSame(logger1, logger2);
    }

    @Test
    void registerPlayerStatusListenerRegistersVelocityListenerWithEventManager() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        EventManager eventManager = mock(EventManager.class);
        Logger logger = mock(Logger.class);
        DataRegistry registry = mock(DataRegistry.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(proxyServer.getEventManager()).thenReturn(eventManager);
        when(registry.getPlayerRepository()).thenReturn(playerRepository);

        VelocityDataRegistry plugin = new TestVelocityListenerRegistrationPlugin(proxyServer, logger, registry);

        plugin.registerPlayerStatusListener();

        verify(eventManager).register(same(plugin), any(PlayerStatusListener.class));
    }

    @Test
    void onProxyInitializeStartsRuntimeAndRegistersBindingsOnSuccess() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataRegistry registry = mock(DataRegistry.class);
        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.fromInstance(any())).thenReturn(Optional.empty());
        when(registry.initialize()).thenReturn(true);

        TestVelocityDataRegistry plugin = new TestVelocityDataRegistry(proxyServer, logger, api, registry);

        plugin.onProxyInitialize(null);

        assertSame(registry, plugin.getDataRegistry());
        assertTrue(plugin.listenerRegistered);
        assertSame(api, plugin.createdWithApi);
        verify(registry).initialize();
    }

    @Test
    void onProxyInitializeLeavesRuntimeUnavailableWhenApiCannotBeResolved() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        Logger logger = mock(Logger.class);
        TestVelocityDataRegistry plugin = new TestVelocityDataRegistry(proxyServer, logger, null, mock(DataRegistry.class));

        plugin.onProxyInitialize(null);

        assertThrows(IllegalStateException.class, plugin::getDataRegistry);
    }

    @Test
    void onProxyInitializeLogsAndExitsWhenRuntimeStartThrows() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataRegistry registry = mock(DataRegistry.class);
        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.fromInstance(any())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("startup failed")).when(registry).initialize();
        TestVelocityDataRegistry plugin = new TestVelocityDataRegistry(proxyServer, logger, api, registry);

        plugin.onProxyInitialize(null);

        verify(logger, atLeastOnce()).error(anyString(), any(Throwable.class));
        assertThrows(IllegalStateException.class, plugin::getDataRegistry);
    }

    @Test
    void onProxyShutdownStopsRuntimeAndShutsDownRegistry() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataRegistry registry = mock(DataRegistry.class);
        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.fromInstance(any())).thenReturn(Optional.empty());
        when(registry.initialize()).thenReturn(true);

        TestVelocityDataRegistry plugin = new TestVelocityDataRegistry(proxyServer, logger, api, registry);
        plugin.onProxyInitialize(null);

        plugin.onProxyShutdown(null);

        assertTrue(plugin.playerEventsDrained);
        verify(registry).shutdown();
        assertThrows(IllegalStateException.class, plugin::getDataRegistry);
    }

    private static final class TestVelocityDataRegistry extends VelocityDataRegistry {
        private final DataProviderAPI resolvedApi;
        private final DataRegistry registry;
        private boolean listenerRegistered;
        private DataProviderAPI createdWithApi;
        private boolean playerEventsDrained;

        private TestVelocityDataRegistry(
                ProxyServer proxyServer,
                Logger logger,
                DataProviderAPI resolvedApi,
                DataRegistry registry
        ) {
            super(proxyServer, logger, TEST_DATA_DIRECTORY);
            this.resolvedApi = resolvedApi;
            this.registry = registry;
        }

        @Override
        DataProviderAPI resolveDataProviderApi() {
            return resolvedApi;
        }

        @Override
        DataRegistry createDataRegistry(DataProviderAPI dataProviderAPI) {
            this.createdWithApi = dataProviderAPI;
            return registry;
        }

        @Override
        void registerPlayerStatusListener() {
            this.listenerRegistered = true;
        }

        @Override
        void stopAcceptingAndDrainPlayerEvents() {
            this.playerEventsDrained = true;
        }
    }

    private static final class TestVelocityListenerRegistrationPlugin extends VelocityDataRegistry {
        private final DataRegistry registry;

        private TestVelocityListenerRegistrationPlugin(ProxyServer proxyServer, Logger logger, DataRegistry registry) {
            super(proxyServer, logger, TEST_DATA_DIRECTORY);
            this.registry = registry;
            initializeLogInstance();
        }

        @Override
        public DataRegistry getDataRegistry() {
            return registry;
        }

        private void initializeLogInstance() {
            try {
                Field field = VelocityDataRegistry.class.getDeclaredField("logInstance");
                field.setAccessible(true);
                field.set(this, super.getPlatformLogger());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
