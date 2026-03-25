package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;

public class DataRegistry {

    private static final String PLAYER_DATA_CONNECTION = "player_data_rw";
    private static final String DEFAULT_ORM_SCHEMA_MODE = "validate";

    private final ILoggerAdapter logger;
    private final String pluginName;
    private final DataProviderAPI dataProviderAPI;
    private final nl.hauntedmc.dataprovider.platform.common.logger.ILoggerAdapter ormLogger;

    private PlayerRepository playerRepository;
    private ORMContext ormContext;

    public DataRegistry(ILoggerAdapter logger, String pluginName, DataProviderAPI dataProviderAPI) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName must not be null");
        this.dataProviderAPI = Objects.requireNonNull(dataProviderAPI, "dataProviderAPI must not be null");
        this.ormLogger = new DataProviderLoggerAdapter(this.logger);
    }

    public synchronized boolean initialize() {
        if (ormContext != null && playerRepository != null) {
            logger.warn("DataRegistry is already initialized.");
            return true;
        }

        try {
            Optional<RelationalDatabaseProvider> providerOptional = dataProviderAPI.registerDatabaseAs(
                    DatabaseType.MYSQL,
                    PLAYER_DATA_CONNECTION,
                    RelationalDatabaseProvider.class
            );
            if (providerOptional.isEmpty()) {
                logger.error("Failed to register relational database provider '" + PLAYER_DATA_CONNECTION + "'.");
                return false;
            }

            DatabaseProvider provider = providerOptional.get();
            if (!provider.isConnected()) {
                logger.error("Database provider '" + PLAYER_DATA_CONNECTION + "' is not connected.");
                return false;
            }

            Optional<DataSource> dataSource = provider.getDataSourceOptional();
            if (dataSource.isEmpty()) {
                logger.error("Relational database provider '" + PLAYER_DATA_CONNECTION + "' returned no DataSource.");
                return false;
            }

            ormContext = new ORMContext(
                    pluginName,
                    dataSource.get(),
                    ormLogger,
                    DEFAULT_ORM_SCHEMA_MODE,
                    PlayerEntity.class,
                    PlayerOnlineStatusEntity.class,
                    PlayerConnectionInfoEntity.class,
                    PlayerSessionEntity.class
            );

            this.playerRepository = new PlayerRepository(ormContext);
            return true;
        } catch (Exception ex) {
            logger.error("Failed to initialize DataRegistry.", ex);
            shutdown();
            return false;
        }
    }

    public synchronized void shutdown() {
        if (ormContext != null) {
            try {
                ormContext.shutdown();
            } catch (Exception ex) {
                logger.warn("Failed to cleanly shut down ORM context.", ex);
            } finally {
                ormContext = null;
                playerRepository = null;
            }
        }
        try {
            dataProviderAPI.unregisterAllDatabases();
        } catch (Exception ex) {
            logger.warn("Failed to unregister DataProvider databases.", ex);
        }
    }

    public ORMContext getORM() {
        return ormContext;
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }

    private static final class DataProviderLoggerAdapter
            implements nl.hauntedmc.dataprovider.platform.common.logger.ILoggerAdapter {

        private final ILoggerAdapter delegate;

        private DataProviderLoggerAdapter(ILoggerAdapter delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public void info(String message) {
            delegate.info(message);
        }

        @Override
        public void warn(String message) {
            delegate.warn(message);
        }

        @Override
        public void error(String message) {
            delegate.error(message);
        }

        @Override
        public void info(String message, Throwable throwable) {
            delegate.info(message, throwable);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            delegate.warn(message, throwable);
        }

        @Override
        public void error(String message, Throwable throwable) {
            delegate.error(message, throwable);
        }
    }
}
