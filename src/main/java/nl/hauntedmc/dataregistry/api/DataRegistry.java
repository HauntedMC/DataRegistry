package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataprovider.logging.LogLevel;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;

/**
 * Core backend runtime that wires DataProvider, ORM, and repositories.
 */
public class DataRegistry {

    private final ILoggerAdapter logger;
    private final String pluginName;
    private final DataProviderAPI dataProviderAPI;
    private final DataRegistrySettings settings;
    private final nl.hauntedmc.dataprovider.logging.LoggerAdapter ormLogger;

    private PlayerRepository playerRepository;
    private ORMContext ormContext;

    public DataRegistry(ILoggerAdapter logger, String pluginName, DataProviderAPI dataProviderAPI) {
        this(logger, pluginName, dataProviderAPI, DataRegistrySettings.defaults());
    }

    public DataRegistry(
            ILoggerAdapter logger,
            String pluginName,
            DataProviderAPI dataProviderAPI,
            DataRegistrySettings settings
    ) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName must not be null");
        this.dataProviderAPI = Objects.requireNonNull(dataProviderAPI, "dataProviderAPI must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.ormLogger = new DataProviderLoggerAdapter(this.logger);
    }

    /**
     * Initializes database registration, ORM context, and repositories.
     *
     * @return {@code true} when initialization completed successfully.
     */
    public synchronized boolean initialize() {
        if (ormContext != null && playerRepository != null) {
            logger.warn("DataRegistry is already initialized.");
            return true;
        }
        if (ormContext != null || playerRepository != null) {
            logger.warn("Detected partially initialized DataRegistry state; forcing cleanup.");
            shutdown();
        }

        try {
            Optional<RelationalDatabaseProvider> providerOptional = dataProviderAPI.registerDatabaseAs(
                    settings.databaseType(),
                    settings.databaseConnectionId(),
                    RelationalDatabaseProvider.class
            );
            if (providerOptional.isEmpty()) {
                return failInitialization("Failed to register relational database provider '" +
                        settings.databaseConnectionId() + "'.");
            }

            DatabaseProvider provider = providerOptional.get();
            if (!provider.isConnected()) {
                return failInitialization("Database provider '" + settings.databaseConnectionId() + "' is not connected.");
            }

            Optional<DataSource> dataSource = provider.getDataSourceOptional();
            if (dataSource.isEmpty()) {
                return failInitialization("Relational database provider '" + settings.databaseConnectionId() +
                        "' returned no DataSource.");
            }

            ormContext = newOrmContext(
                    dataSource.get(),
                    PlayerEntity.class,
                    PlayerOnlineStatusEntity.class,
                    PlayerConnectionInfoEntity.class,
                    PlayerSessionEntity.class
            );

            this.playerRepository = newPlayerRepository(ormContext);
            return true;
        } catch (Exception ex) {
            return failInitialization("Failed to initialize DataRegistry.", ex);
        }
    }

    /**
     * Shuts down ORM resources and unregisters plugin-scoped database registrations.
     */
    public synchronized void shutdown() {
        ORMContext currentOrmContext = ormContext;
        ormContext = null;
        playerRepository = null;

        if (currentOrmContext != null) {
            try {
                currentOrmContext.shutdown();
            } catch (Exception ex) {
                logger.warn("Failed to cleanly shut down ORM context.", ex);
            }
        }
        try {
            dataProviderAPI.unregisterAllDatabasesForPlugin();
        } catch (Exception ex) {
            logger.warn("Failed to unregister DataProvider plugin-scoped databases.", ex);
        }
    }

    private boolean failInitialization(String message) {
        logger.error(message);
        shutdown();
        return false;
    }

    private boolean failInitialization(String message, Exception exception) {
        logger.error(message, exception);
        shutdown();
        return false;
    }

    /**
     * Returns the active ORM context.
     *
     * @throws IllegalStateException when DataRegistry has not been initialized.
     */
    public synchronized ORMContext getORM() {
        if (ormContext == null) {
            throw new IllegalStateException("DataRegistry is not initialized.");
        }
        return ormContext;
    }

    /**
     * Returns the player repository.
     *
     * @throws IllegalStateException when DataRegistry has not been initialized.
     */
    public synchronized PlayerRepository getPlayerRepository() {
        if (playerRepository == null) {
            throw new IllegalStateException("DataRegistry is not initialized.");
        }
        return playerRepository;
    }

    /**
     * Returns immutable runtime settings currently used by this instance.
     */
    public DataRegistrySettings getSettings() {
        return settings;
    }

    /**
     * Returns whether both ORM context and repository are initialized.
     */
    public synchronized boolean isInitialized() {
        return ormContext != null && playerRepository != null;
    }

    ORMContext newOrmContext(DataSource dataSource, Class<?>... entityClasses) {
        return new ORMContext(
                pluginName,
                dataSource,
                ormLogger,
                settings.ormSchemaMode(),
                entityClasses
        );
    }

    PlayerRepository newPlayerRepository(ORMContext context) {
        return new PlayerRepository(context, settings.usernameMaxLength());
    }

    private static final class DataProviderLoggerAdapter
            implements nl.hauntedmc.dataprovider.logging.LoggerAdapter {

        private final ILoggerAdapter delegate;

        private DataProviderLoggerAdapter(ILoggerAdapter delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public void log(LogLevel level, String message, Throwable throwable) {
            if (level == null) {
                delegate.warn(message, throwable);
                return;
            }
            switch (level) {
                case INFO -> {
                    if (throwable == null) {
                        delegate.info(message);
                    } else {
                        delegate.info(message, throwable);
                    }
                }
                case WARN -> {
                    if (throwable == null) {
                        delegate.warn(message);
                    } else {
                        delegate.warn(message, throwable);
                    }
                }
                case ERROR -> {
                    if (throwable == null) {
                        delegate.error(message);
                    } else {
                        delegate.error(message, throwable);
                    }
                }
                default -> delegate.warn(message, throwable);
            }
        }
    }
}
