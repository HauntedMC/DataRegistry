package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNicknameEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionVisitEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.repository.NetworkServiceRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerActivitySummaryRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerConnectionInfoRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerLanguageRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerOnlineStatusRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerNicknameRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerPlaytimeRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerPlaytimeSegmentRepository;
import nl.hauntedmc.dataregistry.backend.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerNameHistoryRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerSessionRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerSessionVisitRepository;
import nl.hauntedmc.dataregistry.api.repository.ServiceInstanceRepository;
import nl.hauntedmc.dataregistry.api.repository.ServiceProbeRepository;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityInitializationTracker;
import nl.hauntedmc.dataregistry.backend.player.DataRegistryQueryExecutor;
import nl.hauntedmc.dataregistry.backend.player.RepositoryPlayerData;
import nl.hauntedmc.dataregistry.backend.player.RepositoryPlayerDirectory;
import nl.hauntedmc.dataregistry.backend.service.DefaultFeatureServiceDirectory;
import nl.hauntedmc.dataregistry.backend.service.PlayerService;
import nl.hauntedmc.dataregistry.backend.service.ServiceRegistryService;
import nl.hauntedmc.dataprovider.logging.LogLevel;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import javax.sql.DataSource;
import java.time.Duration;
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
    private final FeatureServiceDirectory featureServiceDirectory = new DefaultFeatureServiceDirectory();

    private PlayerRepository playerRepository;
    private PlayerActivitySummaryRepository playerActivitySummaryRepository;
    private PlayerOnlineStatusRepository playerOnlineStatusRepository;
    private PlayerConnectionInfoRepository playerConnectionInfoRepository;
    private PlayerLanguageRepository playerLanguageRepository;
    private PlayerNicknameRepository playerNicknameRepository;
    private PlayerNameHistoryRepository playerNameHistoryRepository;
    private PlayerSessionRepository playerSessionRepository;
    private PlayerSessionVisitRepository playerSessionVisitRepository;
    private PlayerPlaytimeRepository playerPlaytimeRepository;
    private PlayerPlaytimeSegmentRepository playerPlaytimeSegmentRepository;
    private NetworkServiceRepository networkServiceRepository;
    private ServiceInstanceRepository serviceInstanceRepository;
    private ServiceProbeRepository serviceProbeRepository;
    private PlayerIdentityInitializationTracker playerIdentityInitializationTracker;
    private DataRegistryQueryExecutor queryExecutor;
    private PlayerDirectory playerDirectory;
    private PlayerData playerData;
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
            this.playerIdentityInitializationTracker = new PlayerIdentityInitializationTracker();
            this.queryExecutor = newQueryExecutor();
            this.playerDirectory = new RepositoryPlayerDirectory(
                    playerRepository,
                    playerIdentityInitializationTracker,
                    queryExecutor
            );
            this.playerActivitySummaryRepository = settings.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY)
                    ? newPlayerActivitySummaryRepository(ormContext)
                    : null;
            this.playerOnlineStatusRepository = settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)
                    ? newPlayerOnlineStatusRepository(ormContext)
                    : null;
            this.playerConnectionInfoRepository = settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)
                    ? newPlayerConnectionInfoRepository(ormContext)
                    : null;
            this.playerLanguageRepository = settings.isFeatureEnabled(DataRegistryFeature.LANGUAGE)
                    ? newPlayerLanguageRepository(ormContext)
                    : null;
            this.playerNicknameRepository = settings.isFeatureEnabled(DataRegistryFeature.NICKNAMES)
                    ? newPlayerNicknameRepository(ormContext)
                    : null;
            this.playerNameHistoryRepository = settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY)
                    ? newPlayerNameHistoryRepository(ormContext)
                    : null;
            this.playerSessionRepository = settings.isFeatureEnabled(DataRegistryFeature.SESSIONS)
                    ? newPlayerSessionRepository(ormContext)
                    : null;
            this.playerSessionVisitRepository = settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS)
                    ? newPlayerSessionVisitRepository(ormContext)
                    : null;
            this.playerPlaytimeRepository = settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME)
                    ? newPlayerPlaytimeRepository(ormContext)
                    : null;
            this.playerPlaytimeSegmentRepository = settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME)
                    ? newPlayerPlaytimeSegmentRepository(ormContext)
                    : null;
            this.playerData = new RepositoryPlayerData(
                    playerDirectory,
                    queryExecutor,
                    ormContext,
                    settings.enabledFeatures(),
                    playerActivitySummaryRepository,
                    playerOnlineStatusRepository,
                    playerConnectionInfoRepository,
                    playerLanguageRepository,
                    playerNicknameRepository,
                    playerNameHistoryRepository,
                    playerPlaytimeRepository,
                    settings.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes()
            );
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
        DataRegistryQueryExecutor currentQueryExecutor = queryExecutor;
        queryExecutor = null;
        if (playerIdentityInitializationTracker != null) {
            playerIdentityInitializationTracker.shutdown();
        }
        playerIdentityInitializationTracker = null;
        playerDirectory = null;
        playerData = null;
        playerActivitySummaryRepository = null;
        playerOnlineStatusRepository = null;
        playerConnectionInfoRepository = null;
        playerLanguageRepository = null;
        playerNicknameRepository = null;
        playerNameHistoryRepository = null;
        playerSessionRepository = null;
        playerSessionVisitRepository = null;
        playerPlaytimeRepository = null;
        playerPlaytimeSegmentRepository = null;
        networkServiceRepository = null;
        serviceInstanceRepository = null;
        serviceProbeRepository = null;
        featureServiceDirectory.clear();

        if (currentQueryExecutor != null) {
            currentQueryExecutor.close();
        }
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
     * Returns the player-centric API for downstream plugins.
     * <p>
     * Prefer this facade over low-level repositories when reading DataRegistry-owned player data.
     *
     * @throws IllegalStateException when DataRegistry has not been initialized.
     */
    public synchronized PlayerData players() {
        if (playerData == null) {
            throw new IllegalStateException("DataRegistry is not initialized.");
        }
        return playerData;
    }

    /**
     * Creates the lifecycle service that owns player row creation, username updates, and active cache changes.
     *
     * @param serviceLogger logger used by the lifecycle service.
     * @return a lifecycle service backed by the initialized player repository.
     * @throws IllegalStateException when DataRegistry has not been initialized.
     */
    public synchronized PlayerService newPlayerService(ILoggerAdapter serviceLogger) {
        if (playerRepository == null) {
            throw new IllegalStateException("DataRegistry is not initialized.");
        }
        return new PlayerService(playerRepository, playerIdentityInitializationTracker, serviceLogger);
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
     * Returns the typed process-local catalog of APIs exported by enabled feature plugins.
     * <p>
     * DataRegistry only owns discovery and cleanup. The registered service implementations and their feature-owned
     * tables remain owned by the exporting feature plugin.
     */
    public FeatureServiceDirectory featureServices() {
        return featureServiceDirectory;
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

    DataRegistryQueryExecutor newQueryExecutor() {
        return new DataRegistryQueryExecutor(
                settings.queryExecutorThreads(),
                Duration.ofMillis(settings.queryTimeoutMillis()),
                settings.queryDevelopmentThreadChecks(),
                logger
        );
    }

    PlayerActivitySummaryRepository newPlayerActivitySummaryRepository(ORMContext context) {
        return new PlayerActivitySummaryRepository(context);
    }

    PlayerOnlineStatusRepository newPlayerOnlineStatusRepository(ORMContext context) {
        return new PlayerOnlineStatusRepository(context);
    }

    PlayerConnectionInfoRepository newPlayerConnectionInfoRepository(ORMContext context) {
        return new PlayerConnectionInfoRepository(context);
    }

    PlayerLanguageRepository newPlayerLanguageRepository(ORMContext context) {
        return new PlayerLanguageRepository(context);
    }

    PlayerNicknameRepository newPlayerNicknameRepository(ORMContext context) {
        return new PlayerNicknameRepository(context);
    }

    PlayerNameHistoryRepository newPlayerNameHistoryRepository(ORMContext context) {
        return new PlayerNameHistoryRepository(context);
    }

    PlayerSessionRepository newPlayerSessionRepository(ORMContext context) {
        return new PlayerSessionRepository(context);
    }

    PlayerSessionVisitRepository newPlayerSessionVisitRepository(ORMContext context) {
        return new PlayerSessionVisitRepository(context);
    }

    PlayerPlaytimeRepository newPlayerPlaytimeRepository(ORMContext context) {
        return new PlayerPlaytimeRepository(
                context,
                settings.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes()
        );
    }

    PlayerPlaytimeSegmentRepository newPlayerPlaytimeSegmentRepository(ORMContext context) {
        return new PlayerPlaytimeSegmentRepository(context);
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
        if (settings.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY)) {
            entityClasses.add(PlayerActivitySummaryEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)) {
            entityClasses.add(PlayerOnlineStatusEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)) {
            entityClasses.add(PlayerConnectionInfoEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.SESSIONS)) {
            entityClasses.add(PlayerSessionEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS)) {
            entityClasses.add(PlayerSessionVisitEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME)) {
            entityClasses.add(PlayerPlaytimeEntity.class);
            entityClasses.add(PlayerPlaytimeSegmentEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.LANGUAGE)) {
            entityClasses.add(PlayerLanguageEntity.class);
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.NICKNAMES)) {
            entityClasses.add(PlayerNicknameEntity.class);
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
                || queryExecutor != null
                || playerIdentityInitializationTracker != null
                || playerDirectory != null
                || playerData != null
                || playerActivitySummaryRepository != null
                || playerOnlineStatusRepository != null
                || playerConnectionInfoRepository != null
                || playerLanguageRepository != null
                || playerNicknameRepository != null
                || playerNameHistoryRepository != null
                || playerSessionRepository != null
                || playerSessionVisitRepository != null
                || playerPlaytimeRepository != null
                || playerPlaytimeSegmentRepository != null
                || networkServiceRepository != null
                || serviceInstanceRepository != null
                || serviceProbeRepository != null;
    }

    private boolean isRuntimeFullyInitialized() {
        if (ormContext == null
                || playerRepository == null
                || queryExecutor == null
                || playerIdentityInitializationTracker == null
                || playerDirectory == null
                || playerData == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY)
                && playerActivitySummaryRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS) && playerOnlineStatusRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO) && playerConnectionInfoRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.LANGUAGE) && playerLanguageRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.NICKNAMES) && playerNicknameRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY) && playerNameHistoryRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.SESSIONS) && playerSessionRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS) && playerSessionVisitRepository == null) {
            return false;
        }
        if (settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME)
                && (playerPlaytimeRepository == null || playerPlaytimeSegmentRepository == null)) {
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
