package nl.hauntedmc.dataregistry;

import nl.hauntedmc.dataprovider.BukkitDataProvider;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.database.base.BaseDatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.orm.ORMContext;
import nl.hauntedmc.dataregistry.entities.GamePlayer;
import nl.hauntedmc.dataregistry.listener.PlayerListener;
import nl.hauntedmc.dataregistry.service.PlayerService;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;

public class DataRegistry extends JavaPlugin {

    private static DataRegistry instance;
    private DataProviderAPI dataProviderAPI;
    ORMContext ormContext;

    @Override
    public void onDisable() {
        ormContext.shutdown();
        dataProviderAPI.unregisterAllDatabases(this.getName());
    }

    @Override
    public void onEnable() {
        instance = this;

        dataProviderAPI = BukkitDataProvider.getDataProviderAPI();
        dataProviderAPI.authenticate(this.getName(), "c5c052c7-b1a3-4c58-8b04-78496b2d4bd8");
        BaseDatabaseProvider provider = dataProviderAPI.registerDatabase(this.getName(), DatabaseType.MYSQL, "test_conn");

        if (provider == null || !provider.isConnected()) {
            getLogger().severe("Database connection not established, disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize ORM with the DataSource and register entity classes.
        RelationalDatabaseProvider relationalProvider = (RelationalDatabaseProvider) provider;
        DataSource dataSource = relationalProvider.getDataSource();

        ormContext = new ORMContext(this.getName(), dataSource, GamePlayer.class);

        PlayerService playerService = new PlayerService(this);
        getServer().getPluginManager().registerEvents(new PlayerListener(playerService), this);


        getLogger().info("dataregistry enabled successfully.");
    }

    public static DataRegistry getInstance() {
        return instance;
    }

    public ORMContext getORM() {
        return ormContext;
    }

}
