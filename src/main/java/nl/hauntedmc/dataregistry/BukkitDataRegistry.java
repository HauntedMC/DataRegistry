package nl.hauntedmc.dataregistry;

import nl.hauntedmc.dataprovider.BukkitDataProvider;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.platform.bukkit.logger.BukkitLoggerAdapter;
import nl.hauntedmc.dataregistry.platform.bukkit.listener.PlayerStatusListener;
import nl.hauntedmc.dataregistry.platform.bukkit.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.bukkit.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitDataRegistry extends JavaPlugin {

    private static DataRegistry dataRegistry;

    @Override
    public void onEnable() {
        DataProviderAPI dataProviderAPI = BukkitDataProvider.getDataProviderAPI();
        BukkitLoggerAdapter logInstance = new BukkitLoggerAdapter(getLogger());
        dataRegistry = new DataRegistry(logInstance, getName(), dataProviderAPI);

        if (!dataRegistry.initialize()) {
            getLogger().severe("Database connection not established, disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize services.
        PlayerService playerService = new PlayerService(this);
        PlayerStatusService statusService = new PlayerStatusService(this);
        String serverName = getServer().getName();

        // Register the join/quit listener.
        getServer().getPluginManager().registerEvents(new PlayerStatusListener(playerService, statusService, serverName), this);

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

    public static PlayerRepository getPlayerRepository() {
        return dataRegistry != null ? dataRegistry.getPlayerRepository() : null;
    }
}
