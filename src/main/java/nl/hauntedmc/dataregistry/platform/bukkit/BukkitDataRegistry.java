package nl.hauntedmc.dataregistry.platform.bukkit;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.platform.bukkit.BukkitDataProvider;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.platform.bukkit.logger.BukkitLoggerAdapter;
import nl.hauntedmc.dataregistry.platform.bukkit.listener.PlayerStatusListener;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerService;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitDataRegistry extends JavaPlugin implements PlatformPlugin {

    private DataRegistry dataRegistry;
    private BukkitLoggerAdapter logInstance;

    @Override
    public void onEnable() {
        DataProviderAPI dataProviderAPI = BukkitDataProvider.getDataProviderAPI();
        logInstance = new BukkitLoggerAdapter(getLogger());
        dataRegistry = new DataRegistry(logInstance, getName(), dataProviderAPI);

        if (!dataRegistry.initialize()) {
            getLogger().severe("Database connection not established, disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize services.
        PlayerService playerService = new PlayerService(this);

        // Register the join/quit listener.
        getServer().getPluginManager().registerEvents(new PlayerStatusListener(this, playerService), this);

        getLogger().info("DataRegistry enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (dataRegistry != null) {
            dataRegistry.shutdown();
        }
    }

    public DataRegistry getDataRegistry() {
        return dataRegistry;
    }

    @Override
    public ILoggerAdapter getPlatformLogger() {
        return logInstance;
    }
}
