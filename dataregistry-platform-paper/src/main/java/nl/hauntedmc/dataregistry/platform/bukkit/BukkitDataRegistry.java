package nl.hauntedmc.dataregistry.platform.bukkit;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderApiSupplier;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceKind;
import nl.hauntedmc.dataregistry.core.service.PlayerService;
import nl.hauntedmc.dataregistry.core.service.ServiceRegistryService;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettingsLoader;
import nl.hauntedmc.dataregistry.platform.bukkit.logger.BukkitLoggerAdapter;
import nl.hauntedmc.dataregistry.platform.bukkit.listener.PlayerStatusListener;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.core.runtime.PlatformDataRegistryRuntime;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class BukkitDataRegistry extends JavaPlugin implements PlatformPlugin {

    private static final String INITIALIZATION_FAILED_MESSAGE =
            "DataRegistry startup failed; disabling plugin.";

    private final PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
    private final DataRegistrySettingsLoader settingsLoader = new DataRegistrySettingsLoader();

    private BukkitLoggerAdapter logInstance;
    private DataRegistrySettings settings;
    private ServiceRegistryService serviceRegistryService;
    private String localServiceInstanceId;
    private BukkitTask serviceRegistryHeartbeatTask;
    private PlayerStatusListener playerStatusListener;

    @Override
    public void onEnable() {
        logInstance = new BukkitLoggerAdapter(getLogger());
        settings = settingsLoader.load(getDataFolder().toPath(), getClass().getClassLoader(), logInstance);

        DataProviderAPI dataProviderAPI = resolveDataProviderApi();
        if (dataProviderAPI == null) {
            logInstance.error("DataProvider API is unavailable; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            runtime.start(
                    () -> createDataRegistry(dataProviderAPI, settings),
                    this::initializeRuntime,
                    logInstance
            );
            startServiceRegistryLifecycle();
        } catch (RuntimeException | Error startupFailure) {
            logInstance.error(INITIALIZATION_FAILED_MESSAGE, startupFailure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logInstance.info("DataRegistry enabled successfully.");
        logInstance.info("Bukkit module runs in bridge mode; full player lifecycle persistence is handled on Velocity.");
    }

    @Override
    public void onDisable() {
        if (playerStatusListener != null) {
            playerStatusListener.shutdown();
            playerStatusListener = null;
        }
        stopServiceRegistryLifecycle();
        runtime.stop(getPlatformLogger());
    }

    DataRegistry createDataRegistry(DataProviderAPI dataProviderAPI, DataRegistrySettings runtimeSettings) {
        return new DataRegistry(logInstance, getName(), dataProviderAPI, runtimeSettings);
    }

    DataProviderAPI resolveDataProviderApi() {
        RegisteredServiceProvider<DataProviderAPI> registration = getServer()
                .getServicesManager()
                .getRegistration(DataProviderAPI.class);
        if (registration != null) {
            return registration.getProvider();
        }

        Plugin dataProviderPlugin = getServer().getPluginManager().getPlugin("DataProvider");
        if (!(dataProviderPlugin instanceof DataProviderApiSupplier supplier)) {
            return null;
        }
        try {
            return supplier.dataProviderApi();
        } catch (RuntimeException exception) {
            logInstance.error("Failed to resolve DataProvider API from Bukkit plugin instance.", exception);
            return null;
        }
    }

    void initializeRuntime(DataRegistry dataRegistry) {
        if (!dataRegistry.initialize()) {
            throw new IllegalStateException("Database connection not established.");
        }

        PlayerService playerService = dataRegistry.newPlayerService(logInstance);
        playerStatusListener = new PlayerStatusListener(
                this,
                playerService,
                settings.bukkitJoinDelayTicks()
        );
        getServer().getPluginManager().registerEvents(playerStatusListener, this);
    }

    void startServiceRegistryLifecycle() {
        if (!settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY)) {
            serviceRegistryService = null;
            localServiceInstanceId = null;
            return;
        }
        if (!settings.bukkitRegisterServiceInstance()) {
            serviceRegistryService = null;
            localServiceInstanceId = null;
            logInstance.info(
                    "Skipping Paper backend service self-registration; Velocity owns backend service identity by default."
            );
            return;
        }
        if (settings.isBukkitServiceNameAuto()) {
            serviceRegistryService = null;
            localServiceInstanceId = null;
            logInstance.warn(
                    "platform.bukkit.register-service-instance is enabled but platform.bukkit.service-name is 'auto'. " +
                            "Skipping backend self-registration to avoid duplicate paper-* service identities. " +
                            "Set platform.bukkit.service-name to the matching Velocity server name to enable it."
            );
            return;
        }
        ServiceRegistryService registryService = runtimeDataRegistry().newServiceRegistryService();
        serviceRegistryService = registryService;
        String instanceId = UUID.randomUUID().toString();
        localServiceInstanceId = instanceId;
        String host = normalizeHost(getServer().getIp());
        Integer port = normalizePort(getServer().getPort());
        String serviceName = settings.bukkitServiceName().trim();

        registryService.refreshRunningInstance(
                ServiceKind.BACKEND,
                serviceName,
                "PAPER",
                instanceId,
                host,
                port
        );

        long intervalTicks = Math.max(20L, settings.serviceHeartbeatIntervalSeconds() * 20L);
        serviceRegistryHeartbeatTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                () -> registryService.refreshRunningInstance(
                        ServiceKind.BACKEND,
                        serviceName,
                        "PAPER",
                        instanceId,
                        host,
                        port
                ),
                intervalTicks,
                intervalTicks
        );
    }

    private static String normalizeHost(String host) {
        if (host != null) {
            String normalized = host.trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        String envHost = System.getenv("HOSTNAME");
        if (envHost != null) {
            String normalized = envHost.trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        String windowsHost = System.getenv("COMPUTERNAME");
        if (windowsHost != null) {
            String normalized = windowsHost.trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        return null;
    }

    private static Integer normalizePort(int port) {
        return port >= 0 && port <= 65535 ? port : null;
    }

    void stopServiceRegistryLifecycle() {
        BukkitTask heartbeatTask = serviceRegistryHeartbeatTask;
        serviceRegistryHeartbeatTask = null;
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }

        ServiceRegistryService registryService = serviceRegistryService;
        serviceRegistryService = null;
        String instanceId = localServiceInstanceId;
        localServiceInstanceId = null;
        if (registryService != null && instanceId != null) {
            registryService.markStopped(instanceId);
        }
    }

    @Override
    public DataRegistryApi getDataRegistry() {
        return runtime.getDataRegistry();
    }

    /**
     * Internal platform wiring access. This method is intentionally not part of {@link PlatformPlugin}.
     */
    protected DataRegistry runtimeDataRegistry() {
        DataRegistryApi api = getDataRegistry();
        if (api instanceof DataRegistry registry) {
            return registry;
        }
        throw new IllegalStateException("Platform runtime did not provide the DataRegistry core implementation.");
    }

    @Override
    public ILoggerAdapter getPlatformLogger() {
        if (logInstance == null) {
            logInstance = new BukkitLoggerAdapter(getLogger());
        }
        return logInstance;
    }
}
