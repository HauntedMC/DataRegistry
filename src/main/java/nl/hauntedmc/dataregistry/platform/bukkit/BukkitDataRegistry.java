package nl.hauntedmc.dataregistry.platform.bukkit;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderApiSupplier;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettingsLoader;
import nl.hauntedmc.dataregistry.platform.bukkit.logger.BukkitLoggerAdapter;
import nl.hauntedmc.dataregistry.platform.bukkit.listener.PlayerStatusListener;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.platform.internal.lifecycle.PlatformDataRegistryRuntime;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitDataRegistry extends JavaPlugin implements PlatformPlugin {

    private static final String INITIALIZATION_FAILED_MESSAGE =
            "DataRegistry startup failed; disabling plugin.";

    private final PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
    private final DataRegistrySettingsLoader settingsLoader = new DataRegistrySettingsLoader();

    private BukkitLoggerAdapter logInstance;
    private DataRegistrySettings settings;

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
        runtime.stop(getPlatformLogger());
    }

    DataRegistry createDataRegistry(DataProviderAPI dataProviderAPI, DataRegistrySettings runtimeSettings) {
        return new DataRegistry(logInstance, getName(), dataProviderAPI, runtimeSettings);
    }

    DataProviderAPI resolveDataProviderApi() {
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

        PlayerService playerService = new PlayerService(
                dataRegistry.getPlayerRepository(),
                logInstance
        );
        getServer().getPluginManager().registerEvents(
                new PlayerStatusListener(this, playerService, settings.bukkitJoinDelayTicks()),
                this
        );
    }

    @Override
    public DataRegistry getDataRegistry() {
        return runtime.getDataRegistry();
    }

    @Override
    public ILoggerAdapter getPlatformLogger() {
        if (logInstance == null) {
            logInstance = new BukkitLoggerAdapter(getLogger());
        }
        return logInstance;
    }
}
