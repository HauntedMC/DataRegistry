package nl.hauntedmc.dataregistry;

import nl.hauntedmc.dataprovider.DataProvider;
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
        dataProviderAPI.unregisterAllDatabases(this);
    }

    @Override
    public void onEnable() {
        instance = this;

        dataProviderAPI = DataProvider.getDataProviderAPI();
        dataProviderAPI.authenticate(this, "82cf4e2c-d67a-450c-a777-97298a404b73");
        BaseDatabaseProvider provider = dataProviderAPI.registerDatabase(this, DatabaseType.MYSQL, "test_conn");

        if (provider == null || !provider.isConnected()) {
            getLogger().severe("Database connection not established, disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize ORM with the DataSource and register entity classes.
        RelationalDatabaseProvider relationalProvider = (RelationalDatabaseProvider) provider;
        DataSource dataSource = relationalProvider.getDataSource();

        ormContext = new ORMContext(this, dataSource, GamePlayer.class);

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
