package nl.hauntedmc.dataregistry.platform.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderApiSupplier;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataregistry.backend.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.backend.service.PlayerNameHistoryService;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.backend.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.backend.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.backend.service.ServiceRegistryService;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettingsLoader;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.platform.internal.lifecycle.PlatformDataRegistryRuntime;
import nl.hauntedmc.dataregistry.platform.velocity.listener.PlayerStatusListener;
import nl.hauntedmc.dataregistry.platform.velocity.logger.SLF4JLoggerAdapter;
import nl.hauntedmc.dataregistry.platform.velocity.util.VelocityPlayerAdapter;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Plugin(
        id = "dataregistry",
        name = "DataRegistry",
        version = "1.6.0",
        description = "DataRegistry for cross-platform data handling.",
        authors = {"HauntedMC"},
        dependencies = @Dependency(id = "dataprovider")
)
public class VelocityDataRegistry implements PlatformPlugin {

    static final int INITIALIZE_EVENT_PRIORITY = 1000;
    static final int SHUTDOWN_EVENT_PRIORITY = -1000;
    static final long EVENT_PIPELINE_DRAIN_TIMEOUT_SECONDS = 5L;
    static final long SERVICE_REGISTRY_SHUTDOWN_TIMEOUT_SECONDS = 2L;

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private final PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
    private final DataRegistrySettingsLoader settingsLoader = new DataRegistrySettingsLoader();

    private SLF4JLoggerAdapter logInstance;
    private DataRegistrySettings settings = DataRegistrySettings.defaults();
    private ExecutorService playerEventExecutor;
    private ScheduledExecutorService serviceRegistryHeartbeatExecutor;
    private PlayerStatusListener playerStatusListener;
    private ServiceRegistryService serviceRegistryService;
    private final AtomicReference<String> localServiceInstanceId = new AtomicReference<>();

    @Inject
    public VelocityDataRegistry(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe(priority = INITIALIZE_EVENT_PRIORITY)
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logInstance = new SLF4JLoggerAdapter(logger);
        VelocityPlayerAdapter.setProxy(proxyServer);
        settings = settingsLoader.load(dataDirectory, getClass().getClassLoader(), logInstance);

        DataProviderAPI dataProviderAPI = resolveDataProviderApi();
        if (dataProviderAPI == null) {
            logger.error("DataProvider API not available; DataRegistry startup aborted.");
            return;
        }

        try {
            runtime.start(
                    () -> createDataRegistry(dataProviderAPI),
                    this::initializeRuntime,
                    getPlatformLogger()
            );
            registerPlayerStatusListener();
            startServiceRegistryLifecycle();
        } catch (RuntimeException | Error startupFailure) {
            shutdownPlayerEventExecutor();
            shutdownServiceRegistryHeartbeatExecutor();
            logger.error("DataRegistry startup failed on Velocity.", startupFailure);
            return;
        }

        logger.info("DataRegistry enabled successfully on Velocity.");
        logger.info("Enabled built-in data domains: {}", settings.enabledFeatures());
    }

    @Subscribe(priority = SHUTDOWN_EVENT_PRIORITY)
    public void onProxyShutdown(ProxyShutdownEvent event) {
        stopAcceptingAndDrainPlayerEvents();
        stopServiceRegistryLifecycle();
        runtime.stop(getPlatformLogger());
        shutdownPlayerEventExecutor();
        shutdownServiceRegistryHeartbeatExecutor();
        logger.info("DataRegistry disabled on Velocity.");
    }

    @Override
    public DataRegistry getDataRegistry() {
        return runtime.getDataRegistry();
    }

    @Override
    public ILoggerAdapter getPlatformLogger() {
        if (logInstance == null) {
            logInstance = new SLF4JLoggerAdapter(logger);
        }
        return logInstance;
    }

    static String resolvePluginVersion(ProxyServer proxyServer, Object pluginInstance) {
        return proxyServer.getPluginManager()
                .fromInstance(pluginInstance)
                .map(PluginContainer::getDescription)
                .flatMap(description -> description.getVersion())
                .orElse("unknown");
    }

    DataProviderAPI resolveDataProviderApi() {
        Optional<DataProviderApiSupplier> supplier = proxyServer.getPluginManager()
                .getPlugin("dataprovider")
                .flatMap(container -> container.getInstance()
                        .filter(instance -> instance instanceof DataProviderApiSupplier)
                        .map(instance -> (DataProviderApiSupplier) instance));

        if (supplier.isEmpty()) {
            logger.error("Failed to resolve DataProvider API supplier from plugin container.");
            return null;
        }

        try {
            return supplier.get().dataProviderApi();
        } catch (RuntimeException exception) {
            logger.error("Failed to resolve DataProvider API from supplier.", exception);
            return null;
        }
    }

    DataRegistry createDataRegistry(DataProviderAPI dataProviderAPI) {
        String pluginVersion = resolvePluginVersion(proxyServer, this);
        logger.info("Booting DataRegistry version {}.", pluginVersion);
        return new DataRegistry(getPlatformLogger(), "DataRegistry", dataProviderAPI, settings);
    }

    void registerPlayerStatusListener() {
        ensurePlayerEventExecutor();
        DataRegistry registry = getDataRegistry();
        PlayerService playerService = new PlayerService(registry.getPlayerRepository(), getPlatformLogger());
        PlayerNameHistoryService nameHistoryService = new PlayerNameHistoryService(
                registry,
                getPlatformLogger(),
                settings.usernameMaxLength(),
                settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY)
        );
        PlayerStatusService statusService = new PlayerStatusService(
                registry,
                getPlatformLogger(),
                settings.serverNameMaxLength(),
                settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)
        );
        PlayerConnectionInfoService connectionService = new PlayerConnectionInfoService(
                registry,
                getPlatformLogger(),
                settings.persistIpAddress(),
                settings.persistVirtualHost(),
                settings.ipAddressMaxLength(),
                settings.virtualHostMaxLength(),
                settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)
        );
        PlayerSessionService sessionService = new PlayerSessionService(
                registry,
                getPlatformLogger(),
                settings.persistIpAddress(),
                settings.persistVirtualHost(),
                settings.ipAddressMaxLength(),
                settings.virtualHostMaxLength(),
                settings.serverNameMaxLength(),
                settings.isFeatureEnabled(DataRegistryFeature.SESSIONS)
        );

        PlayerStatusListener listener = new PlayerStatusListener(
                playerService,
                nameHistoryService,
                statusService,
                connectionService,
                sessionService,
                getPlatformLogger(),
                playerEventExecutor
        );
        proxyServer.getEventManager().register(this, listener);
        playerStatusListener = listener;
    }

    void stopAcceptingAndDrainPlayerEvents() {
        PlayerStatusListener listener = playerStatusListener;
        playerStatusListener = null;
        if (listener == null) {
            return;
        }
        listener.beginShutdown();
        boolean drained = listener.awaitPipelineDrain(EVENT_PIPELINE_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!drained) {
            logger.warn("Timed out waiting for queued player lifecycle events to drain before shutdown.");
        }
    }

    private void initializeRuntime(DataRegistry registry) {
        if (!registry.initialize()) {
            throw new IllegalStateException("Database connection not established.");
        }
    }

    private void startServiceRegistryLifecycle() {
        if (!settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY)) {
            serviceRegistryService = null;
            localServiceInstanceId.set(null);
            return;
        }
        ensureServiceRegistryHeartbeatExecutor();
        DataRegistry registry = getDataRegistry();
        ServiceRegistryService registryService = registry.newServiceRegistryService();
        serviceRegistryService = registryService;

        String instanceId = java.util.UUID.randomUUID().toString();
        localServiceInstanceId.set(instanceId);
        InetSocketAddress address = proxyServer.getBoundAddress();
        String host = address == null ? null : address.getHostString();
        Integer port = address == null ? null : address.getPort();
        String serviceName = resolveProxyServiceName(host, port);
        int heartbeatIntervalSeconds = settings.serviceHeartbeatIntervalSeconds();

        registryService.refreshRunningInstance(
                ServiceKind.PROXY,
                serviceName,
                "VELOCITY",
                instanceId,
                host,
                port
        );
        serviceRegistryHeartbeatExecutor.scheduleAtFixedRate(
                () -> registryService.refreshRunningInstance(
                        ServiceKind.PROXY,
                        serviceName,
                        "VELOCITY",
                        instanceId,
                        host,
                        port
                ),
                heartbeatIntervalSeconds,
                heartbeatIntervalSeconds,
                TimeUnit.SECONDS
        );
    }

    private static String resolveProxyServiceName(String host, Integer port) {
        String hostPart = host == null || host.isBlank() ? "unknown-host" : host;
        String portPart = port == null ? "unknown-port" : Integer.toString(port);
        return "velocity-" + hostPart + ":" + portPart;
    }

    private void stopServiceRegistryLifecycle() {
        ServiceRegistryService registryService = serviceRegistryService;
        serviceRegistryService = null;
        String instanceId = localServiceInstanceId.getAndSet(null);
        if (registryService == null || instanceId == null) {
            return;
        }
        registryService.markStopped(instanceId);
    }

    private void ensurePlayerEventExecutor() {
        if (playerEventExecutor != null && !playerEventExecutor.isShutdown()) {
            return;
        }
        int workerCount = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger workerIndex = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("DataRegistry-velocity-events-" + workerIndex.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        playerEventExecutor = Executors.newFixedThreadPool(workerCount, threadFactory);
    }

    private void ensureServiceRegistryHeartbeatExecutor() {
        if (serviceRegistryHeartbeatExecutor != null && !serviceRegistryHeartbeatExecutor.isShutdown()) {
            return;
        }
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("DataRegistry-velocity-service-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
        serviceRegistryHeartbeatExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    private void shutdownPlayerEventExecutor() {
        ExecutorService executor = playerEventExecutor;
        playerEventExecutor = null;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void shutdownServiceRegistryHeartbeatExecutor() {
        ScheduledExecutorService executor = serviceRegistryHeartbeatExecutor;
        serviceRegistryHeartbeatExecutor = null;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SERVICE_REGISTRY_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
