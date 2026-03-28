package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.api.repository.NetworkServiceRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerNameHistoryRepository;
import nl.hauntedmc.dataregistry.api.repository.ServiceInstanceRepository;
import nl.hauntedmc.dataregistry.api.repository.ServiceProbeRepository;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.backend.service.ServiceRegistryService;
import nl.hauntedmc.dataprovider.logging.LogLevel;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private PlayerNameHistoryRepository playerNameHistoryRepository;
    private NetworkServiceRepository networkServiceRepository;
    private ServiceInstanceRepository serviceInstanceRepository;
    private ServiceProbeRepository serviceProbeRepository;
    private ORMContext ormContext;
    private ORMContext serviceOrmContext;

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
        this.pluginName = normalizePluginName(pluginName);
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
        if (isRuntimeFullyInitialized()) {
            logger.warn("DataRegistry is already initialized.");
            return true;
        }
        if (hasAnyInitializedState()) {
            logger.warn("Detected partially initialized DataRegistry state; forcing cleanup.");
            shutdown();
        }

        try {
            Map<String, DataSource> dataSources = new HashMap<>();
            DataSource playerDataSource = resolveDataSource(dataSources, settings.playerDatabaseConnectionId());
            ormContext = newOrmContext(playerDataSource, resolvePlayerOrmEntityClasses());
            serviceOrmContext = null;

            this.playerRepository = newPlayerRepository(ormContext);
            this.playerNameHistoryRepository = settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY)
                    ? newPlayerNameHistoryRepository(ormContext)
                    : null;
            this.networkServiceRepository = null;
            this.serviceInstanceRepository = null;
            this.serviceProbeRepository = null;

            if (settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY)) {
                DataSource serviceDataSource = resolveDataSource(dataSources, settings.serviceDatabaseConnectionId());
                serviceOrmContext = newServiceOrmContext(serviceDataSource, resolveServiceOrmEntityClasses());
                this.networkServiceRepository = newNetworkServiceRepository(serviceOrmContext);
                this.serviceInstanceRepository = newServiceInstanceRepository(serviceOrmContext);
                this.serviceProbeRepository = newServiceProbeRepository(serviceOrmContext);
            }
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
        ORMContext currentServiceOrmContext = serviceOrmContext;
        ormContext = null;
        serviceOrmContext = null;
        playerRepository = null;
        playerNameHistoryRepository = null;
        networkServiceRepository = null;
        serviceInstanceRepository = null;
        serviceProbeRepository = null;

        if (currentServiceOrmContext != null) {
            try {
                currentServiceOrmContext.shutdown();
            } catch (Exception ex) {
                logger.warn("Failed to cleanly shut down service ORM context.", ex);
            }
        }
        if (currentOrmContext != null) {
            try {
                currentOrmContext.shutdown();
            } catch (Exception ex) {
                logger.warn("Failed to cleanly shut down player ORM context.", ex);
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

    public synchronized PlayerNameHistoryRepository getPlayerNameHistoryRepository() {
        if (playerNameHistoryRepository == null) {
            throw new IllegalStateException("Player name history repository is unavailable.");
        }
        return playerNameHistoryRepository;
    }

    public synchronized ORMContext getServiceORM() {
        if (serviceOrmContext == null) {
            throw new IllegalStateException("Service ORM context is unavailable.");
        }
        return serviceOrmContext;
    }

    public synchronized NetworkServiceRepository getNetworkServiceRepository() {
        if (networkServiceRepository == null) {
            throw new IllegalStateException("Network service repository is unavailable.");
        }
        return networkServiceRepository;
    }

    public synchronized ServiceInstanceRepository getServiceInstanceRepository() {
        if (serviceInstanceRepository == null) {
            throw new IllegalStateException("Service instance repository is unavailable.");
        }
        return serviceInstanceRepository;
    }

    public synchronized ServiceProbeRepository getServiceProbeRepository() {
        if (serviceProbeRepository == null) {
            throw new IllegalStateException("Service probe repository is unavailable.");
        }
        return serviceProbeRepository;
    }

    /**
     * Creates a helper facade for service-registry writes and read-side discovery helpers.
     */
    public ServiceRegistryService newServiceRegistryService() {
        return new ServiceRegistryService(this, logger, settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
    }

    /**
     * Returns immutable runtime settings currently used by this instance.
     */
    public DataRegistrySettings getSettings() {
        return settings;
    }

    /**
     * Returns the enabled built-in features for this runtime instance.
     */
    public Set<DataRegistryFeature> getEnabledFeatures() {
        return settings.enabledFeatures();
    }

    /**
     * Returns whether a built-in feature is enabled for this runtime instance.
     */
    public boolean isFeatureEnabled(DataRegistryFeature feature) {
        return settings.isFeatureEnabled(feature);
    }

    /**
     * Returns whether both ORM context and repository are initialized.
     */
    public synchronized boolean isInitialized() {
        return isRuntimeFullyInitialized();
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

    PlayerNameHistoryRepository newPlayerNameHistoryRepository(ORMContext context) {
        return new PlayerNameHistoryRepository(context);
    }

    NetworkServiceRepository newNetworkServiceRepository(ORMContext context) {
        return new NetworkServiceRepository(context);
    }

    ServiceInstanceRepository newServiceInstanceRepository(ORMContext context) {
        return new ServiceInstanceRepository(context);
    }

    ServiceProbeRepository newServiceProbeRepository(ORMContext context) {
        return new ServiceProbeRepository(context);
    }

    ORMContext newServiceOrmContext(DataSource dataSource, Class<?>... entityClasses) {
        return new ORMContext(
                pluginName + "-service",
                dataSource,
                ormLogger,
                settings.ormSchemaMode(),
                entityClasses
        );
    }

    private Class<?>[] resolvePlayerOrmEntityClasses() {
        LinkedHashSet<Class<?>> entityClasses = new LinkedHashSet<>();
        entityClasses.add(PlayerEntity.class);
        if (settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)) {
            entityClasses.add(PlayerOnlineStatusEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)) {
            entityClasses.add(PlayerConnectionInfoEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.SESSIONS)) {
            entityClasses.add(PlayerSessionEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY)) {
            entityClasses.add(PlayerNameHistoryEntity.class);
        }
        return entityClasses.toArray(Class<?>[]::new);
    }

    private Class<?>[] resolveServiceOrmEntityClasses() {
        return new Class<?>[]{
                NetworkServiceEntity.class,
                ServiceInstanceEntity.class,
                ServiceProbeEntity.class
        };
    }

    private DataSource resolveDataSource(Map<String, DataSource> dataSourceCache, String connectionId) {
        DataSource cached = dataSourceCache.get(connectionId);
        if (cached != null) {
            return cached;
        }

        Optional<RelationalDatabaseProvider> providerOptional = dataProviderAPI.registerDatabaseAs(
                settings.databaseType(),
                connectionId,
                RelationalDatabaseProvider.class
        );
        if (providerOptional.isEmpty()) {
            throw new IllegalStateException(
                    "Failed to register relational database provider '" + connectionId + "'."
            );
        }

        RelationalDatabaseProvider provider = providerOptional.get();
        if (!provider.isConnected()) {
            throw new IllegalStateException("Database provider '" + connectionId + "' is not connected.");
        }

        DataSource dataSource = provider.getDataSourceOptional()
                .orElseThrow(() -> new IllegalStateException(
                        "Relational database provider '" + connectionId + "' returned no DataSource."
                ));
        dataSourceCache.put(connectionId, dataSource);
        return dataSource;
    }

    private boolean hasAnyInitializedState() {
        return ormContext != null
                || serviceOrmContext != null
                || playerRepository != null
                || playerNameHistoryRepository != null
                || networkServiceRepository != null
                || serviceInstanceRepository != null
                || serviceProbeRepository != null;
    }

    private boolean isRuntimeFullyInitialized() {
        if (ormContext == null || playerRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY) && playerNameHistoryRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY)) {
            return serviceOrmContext != null
                    && networkServiceRepository != null
                    && serviceInstanceRepository != null
                    && serviceProbeRepository != null;
        }
        return true;
    }

    private static String normalizePluginName(String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName must not be null");
        String normalized = pluginName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("pluginName must not be blank");
        }
        return normalized;
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
