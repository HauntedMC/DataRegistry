package nl.hauntedmc.dataregistry.platform.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderApiSupplier;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerLifecycleOutboxRepository;
import nl.hauntedmc.dataregistry.core.persistence.repository.ServiceProbeRepository;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerIdentityInitializationTracker;
import nl.hauntedmc.dataregistry.core.service.ServiceRegistryService;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.platform.velocity.listener.PlayerStatusListener;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void resolveBackendProbeServiceNameUsesConfiguredVelocityNameWhenPresent() throws ReflectiveOperationException {
        Method method = VelocityDataRegistry.class.getDeclaredMethod(
                "resolveBackendProbeServiceName",
                String.class,
                String.class,
                Integer.class
        );
        method.setAccessible(true);

        String resolved = (String) method.invoke(null, " lobby-01 ", "10.0.0.5", 25565);

        assertEquals("lobby-01", resolved);
    }

    @Test
    void resolveBackendProbeServiceNameFallsBackToEndpointWhenNameMissing() throws ReflectiveOperationException {
        Method method = VelocityDataRegistry.class.getDeclaredMethod(
                "resolveBackendProbeServiceName",
                String.class,
                String.class,
                Integer.class
        );
        method.setAccessible(true);

        String resolved = (String) method.invoke(null, " ", "10.0.0.5", 25565);
        String unresolved = (String) method.invoke(null, null, null, null);

        assertEquals("paper-10.0.0.5:25565", resolved);
        assertEquals("paper-unknown-host:unknown-port", unresolved);
    }

    @Test
    void resolveDataProviderApiReturnsPluginBoundSupplierApi() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        PluginContainer pluginContainer = mock(PluginContainer.class);
        Logger logger = mock(Logger.class);
        DataProviderApiSupplier supplier = mock(DataProviderApiSupplier.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderAPI pluginApi = mock(DataProviderAPI.class);

        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("dataprovider")).thenReturn(Optional.of(pluginContainer));
        doReturn(Optional.of(supplier)).when(pluginContainer).getInstance();
        when(supplier.dataProviderApi()).thenReturn(api);

        VelocityDataRegistry plugin = new VelocityDataRegistry(proxyServer, logger, TEST_DATA_DIRECTORY);

        when(api.forPlugin(plugin)).thenReturn(pluginApi);

        assertSame(pluginApi, plugin.resolveDataProviderApi());
        verify(api).forPlugin(plugin);
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
    void registerDataRegistryCommandRegistersDataRegistryAndDrAliases() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        CommandManager commandManager = mock(CommandManager.class);
        CommandMeta.Builder commandBuilder = mock(CommandMeta.Builder.class);
        CommandMeta commandMeta = mock(CommandMeta.class);
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                proxyServer,
                mock(Logger.class),
                TEST_DATA_DIRECTORY
        );
        when(proxyServer.getCommandManager()).thenReturn(commandManager);
        when(commandManager.metaBuilder(any(BrigadierCommand.class))).thenReturn(commandBuilder);
        when(commandBuilder.aliases("dr")).thenReturn(commandBuilder);
        when(commandBuilder.plugin(plugin)).thenReturn(commandBuilder);
        when(commandBuilder.build()).thenReturn(commandMeta);

        plugin.registerDataRegistryCommand();

        verify(commandManager).register(same(commandMeta), any(BrigadierCommand.class));
    }

    @Test
    void registerPlayerStatusListenerRegistersVelocityListenerWithEventManager() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        EventManager eventManager = mock(EventManager.class);
        Logger logger = mock(Logger.class);
        DataRegistry registry = mock(DataRegistry.class);
        nl.hauntedmc.dataregistry.core.service.PlayerService playerService =
                new nl.hauntedmc.dataregistry.core.service.PlayerService(
                        mock(PlayerRepository.class),
                        new PlayerIdentityInitializationTracker(),
                        mock(ILoggerAdapter.class)
                );
        when(proxyServer.getEventManager()).thenReturn(eventManager);
        when(registry.newPlayerService(any())).thenReturn(playerService);

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
        writeTestConfig("features:\n  service-registry: false\n");
        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.fromInstance(any())).thenReturn(Optional.empty());
        when(registry.initialize()).thenReturn(true);

        TestVelocityDataRegistry plugin = new TestVelocityDataRegistry(proxyServer, logger, api, registry);

        plugin.onProxyInitialize(null);

        assertSame(registry, plugin.getDataRegistry());
        assertTrue(plugin.listenerRegistered);
        assertTrue(plugin.playerPresenceRecoveryInvoked);
        assertEquals(List.of("recover-presence", "register-listener"), plugin.startupSteps);
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
    void onProxyInitializeRollsBackIfStartupFailsDuringPresenceRecovery() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataRegistry registry = mock(DataRegistry.class);
        when(proxyServer.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.fromInstance(any())).thenReturn(Optional.empty());
        when(registry.initialize()).thenReturn(true);

        TestVelocityDataRegistry plugin = new TestVelocityDataRegistry(proxyServer, logger, api, registry);
        plugin.failDuringPlayerPresenceRecovery = true;

        plugin.onProxyInitialize(null);

        assertFalse(plugin.listenerRegistered);
        assertTrue(plugin.playerEventsDrained);
        verify(registry).shutdown();
        assertThrows(IllegalStateException.class, plugin::getDataRegistry);
    }

    @Test
    void onProxyShutdownStopsRuntimeAndShutsDownRegistry() {
        ProxyServer proxyServer = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataRegistry registry = mock(DataRegistry.class);
        writeTestConfig("features:\n  service-registry: false\n");
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

    @Test
    void stopAcceptingAndDrainPlayerEventsClosesActivePresenceAfterQueuedWorkDrains()
            throws ReflectiveOperationException {
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                mock(ProxyServer.class),
                mock(Logger.class),
                TEST_DATA_DIRECTORY
        );
        PlayerStatusListener listener = mock(PlayerStatusListener.class);
        when(listener.awaitPipelineDrain(VelocityDataRegistry.EVENT_PIPELINE_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .thenReturn(true, true);
        Field listenerField = VelocityDataRegistry.class.getDeclaredField("playerStatusListener");
        listenerField.setAccessible(true);
        listenerField.set(plugin, listener);

        plugin.stopAcceptingAndDrainPlayerEvents();

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).beginShutdown();
        inOrder.verify(listener).awaitPipelineDrain(
                VelocityDataRegistry.EVENT_PIPELINE_DRAIN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        inOrder.verify(listener).closeActivePresenceForShutdown();
        inOrder.verify(listener).awaitPipelineDrain(
                VelocityDataRegistry.EVENT_PIPELINE_DRAIN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    @Test
    void stopServiceRegistryLifecycleWaitsForHeartbeatBeforeMarkingInstanceStopped()
            throws ReflectiveOperationException, InterruptedException {
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                mock(ProxyServer.class),
                mock(Logger.class),
                TEST_DATA_DIRECTORY
        );
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext serviceOrm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<nl.hauntedmc.dataregistry.core.persistence.entity.ServiceInstanceEntity> instanceQuery = mock(Query.class);
        when(registry.getServiceORM()).thenReturn(serviceOrm);
        doAnswer(invocation -> ((java.util.function.Function<Session, ?>) invocation.getArgument(0)).apply(session))
                .when(serviceOrm)
                .runInTransaction(any());
        when(session.createQuery(anyString(), eq(nl.hauntedmc.dataregistry.core.persistence.entity.ServiceInstanceEntity.class)))
                .thenReturn(instanceQuery);
        when(instanceQuery.setParameter(anyString(), any())).thenReturn(instanceQuery);
        when(instanceQuery.setMaxResults(anyInt())).thenReturn(instanceQuery);
        ServiceRegistryService registryService = new ServiceRegistryService(registry, mock(ILoggerAdapter.class), true);
        ScheduledExecutorService heartbeatExecutor = mock(ScheduledExecutorService.class);
        ScheduledExecutorService probeExecutor = mock(ScheduledExecutorService.class);
        when(heartbeatExecutor.awaitTermination(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(probeExecutor.awaitTermination(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        setField(plugin, "serviceRegistryService", registryService);
        setField(plugin, "serviceRegistryHeartbeatExecutor", heartbeatExecutor);
        setField(plugin, "serviceRegistryProbeExecutor", probeExecutor);
        @SuppressWarnings("unchecked")
        AtomicReference<String> instanceId = (AtomicReference<String>) getField(plugin, "localServiceInstanceId");
        instanceId.set("instance-1");

        Method stopMethod = VelocityDataRegistry.class.getDeclaredMethod("stopServiceRegistryLifecycle");
        stopMethod.setAccessible(true);
        stopMethod.invoke(plugin);

        InOrder inOrder = inOrder(heartbeatExecutor, probeExecutor, registry);
        inOrder.verify(heartbeatExecutor).shutdown();
        inOrder.verify(probeExecutor).shutdown();
        inOrder.verify(registry).getServiceORM();
    }

    @Test
    void purgeLifecycleOutboxUsesConfiguredRetentionAndBoundedBatch() throws ReflectiveOperationException {
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                mock(ProxyServer.class),
                mock(Logger.class),
                TEST_DATA_DIRECTORY
        );
        DataRegistry registry = mock(DataRegistry.class);
        PlayerLifecycleOutboxRepository repository = mock(PlayerLifecycleOutboxRepository.class);
        when(registry.getPlayerLifecycleOutboxRepository()).thenReturn(repository);

        Method purgeMethod = VelocityDataRegistry.class.getDeclaredMethod(
                "purgeLifecycleOutbox",
                DataRegistry.class,
                int.class,
                int.class
        );
        purgeMethod.setAccessible(true);
        purgeMethod.invoke(plugin, registry, 14, 123);

        verify(repository).deleteCreatedBefore(any(Instant.class), eq(123));
    }

    @Test
    void purgeClosedSessionHistoryUsesConfiguredRetentionAndBoundedBatch() throws ReflectiveOperationException {
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                mock(ProxyServer.class),
                mock(Logger.class),
                TEST_DATA_DIRECTORY
        );
        DataRegistry registry = mock(DataRegistry.class);

        Method purgeMethod = VelocityDataRegistry.class.getDeclaredMethod(
                "purgeClosedSessionHistory",
                DataRegistry.class,
                int.class,
                int.class
        );
        purgeMethod.setAccessible(true);
        purgeMethod.invoke(plugin, registry, 30, 123);

        verify(registry).purgeClosedSessionHistoryOlderThan(Duration.ofDays(30), 123);
    }

    @Test
    void purgeStaleProbesIfDueRunsAtMostOncePerConfiguredInterval() throws ReflectiveOperationException {
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                mock(ProxyServer.class),
                mock(Logger.class),
                TEST_DATA_DIRECTORY
        );
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter platformLogger = mock(ILoggerAdapter.class);
        ServiceProbeRepository probeRepository = mock(ServiceProbeRepository.class);
        when(registry.getServiceProbeRepository()).thenReturn(probeRepository);
        when(probeRepository.deleteCheckedBefore(any(), anyInt())).thenReturn(500);
        ServiceRegistryService registryService = new ServiceRegistryService(registry, platformLogger, true);

        Method purgeMethod = VelocityDataRegistry.class.getDeclaredMethod(
                "purgeStaleProbesIfDue",
                ServiceRegistryService.class,
                int.class,
                int.class
        );
        purgeMethod.setAccessible(true);

        purgeMethod.invoke(plugin, registryService, 72, 12);
        purgeMethod.invoke(plugin, registryService, 72, 12);

        verify(probeRepository).deleteCheckedBefore(any(), eq(500));
    }

    @Test
    void disabledProbeRetentionDoesNotDeleteProbeHistory() throws ReflectiveOperationException {
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                mock(ProxyServer.class),
                mock(Logger.class),
                TEST_DATA_DIRECTORY
        );
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter platformLogger = mock(ILoggerAdapter.class);
        ServiceProbeRepository probeRepository = mock(ServiceProbeRepository.class);
        when(registry.getServiceProbeRepository()).thenReturn(probeRepository);
        ServiceRegistryService registryService = new ServiceRegistryService(registry, platformLogger, true);

        Method purgeMethod = VelocityDataRegistry.class.getDeclaredMethod(
                "purgeStaleProbesIfDue",
                ServiceRegistryService.class,
                int.class,
                int.class
        );
        purgeMethod.setAccessible(true);
        purgeMethod.invoke(plugin, registryService, -1, 12);

        verifyNoInteractions(probeRepository);
    }

    private static final class TestVelocityDataRegistry extends VelocityDataRegistry {
        private final DataProviderAPI resolvedApi;
        private final DataRegistry registry;
        private boolean listenerRegistered;
        private DataProviderAPI createdWithApi;
        private boolean playerEventsDrained;
        private boolean playerPresenceRecoveryInvoked;
        private boolean failDuringPlayerPresenceRecovery;
        private final List<String> startupSteps = new ArrayList<>();

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
            startupSteps.add("register-listener");
            this.listenerRegistered = true;
        }

        @Override
        void registerDataRegistryCommand() {
            // Command-manager wiring is covered independently; this startup fixture has no command manager.
        }

        @Override
        void stopAcceptingAndDrainPlayerEvents() {
            this.playerEventsDrained = true;
        }

        @Override
        void recoverPlayerPresenceStateOnStartup() {
            startupSteps.add("recover-presence");
            this.playerPresenceRecoveryInvoked = true;
            if (failDuringPlayerPresenceRecovery) {
                throw new IllegalStateException("player presence recovery failed");
            }
        }
    }

    private static Object getField(Object target, String name) throws ReflectiveOperationException {
        Field field = VelocityDataRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = VelocityDataRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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

    private static void writeTestConfig(String configContent) {
        try {
            Files.createDirectories(TEST_DATA_DIRECTORY);
            Files.writeString(TEST_DATA_DIRECTORY.resolve("config.yml"), configContent);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
