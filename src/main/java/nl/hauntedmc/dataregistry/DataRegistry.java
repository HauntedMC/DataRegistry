package nl.hauntedmc.dataregistry;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.database.base.BaseDatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.orm.ORMContext;
import nl.hauntedmc.dataprovider.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.repository.impl.PlayerRepository;

import javax.sql.DataSource;

public class DataRegistry {

    private static ILoggerAdapter logInstance;

    private final String pluginName;
    private final DataProviderAPI dataProviderAPI;

    private PlayerRepository playerRepository;
    private ORMContext ormContext;

    public DataRegistry(ILoggerAdapter logger, String pluginName, DataProviderAPI dataProviderAPI) {
        logInstance = logger;
        this.pluginName = pluginName;
        this.dataProviderAPI = dataProviderAPI;
    }

    /**
     * Authenticate, register database and create ORMContext.
     */
    public boolean initialize() {
        dataProviderAPI.authenticate(pluginName, "c5c052c7-b1a3-4c58-8b04-78496b2d4bd8");
        BaseDatabaseProvider provider = dataProviderAPI.registerDatabase(pluginName, DatabaseType.MYSQL, "test_conn");

        if (provider == null || !provider.isConnected()) {
            getLogger().error("Database Provider is not connected.");
            return false;
        }
        RelationalDatabaseProvider relationalProvider = (RelationalDatabaseProvider) provider;
        DataSource dataSource = relationalProvider.getDataSource();

        ormContext = new ORMContext(pluginName, dataSource, PlayerEntity.class, PlayerOnlineStatusEntity.class);

        this.playerRepository = new PlayerRepository(ormContext);

        return true;
    }

    public void shutdown() {
        if (ormContext != null) {
            ormContext.shutdown();
        }
        dataProviderAPI.unregisterAllDatabases(pluginName);
    }

    public static ILoggerAdapter getLogger() {
        return logInstance;
    }

    public ORMContext getORM() {
        return ormContext;
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }
}
