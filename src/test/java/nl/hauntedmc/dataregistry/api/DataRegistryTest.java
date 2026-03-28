package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.logging.LogLevel;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.api.repository.NetworkServiceRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerNameHistoryRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.api.repository.ServiceInstanceRepository;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.backend.service.ServiceRegistryService;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRegistryTest {

    @Test
    void constructorRejectsNullArguments() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataRegistrySettings settings = DataRegistrySettings.defaults();

        assertThrows(NullPointerException.class, () -> new DataRegistry(null, "DataRegistry", api));
        assertThrows(NullPointerException.class, () -> new DataRegistry(logger, null, api));
        assertThrows(NullPointerException.class, () -> new DataRegistry(logger, "DataRegistry", null));
        assertThrows(NullPointerException.class, () -> new DataRegistry(logger, "DataRegistry", api, null));
        assertThrows(IllegalArgumentException.class, () -> new DataRegistry(logger, "   ", api));
        assertDoesNotThrow(() -> new DataRegistry(logger, "DataRegistry", api, settings));
    }

    @Test
    void initializeCreatesOrmContextAndRepositoryWhenProviderIsReady() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = mock(PlayerRepository.class);

        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));

        TestableDataRegistry registry = new TestableDataRegistry(logger, "DataRegistry", api, ormContext, repository);

        assertTrue(registry.initialize());
        assertSame(ormContext, registry.getORM());
        assertSame(repository, registry.getPlayerRepository());
        assertSame(dataSource, registry.lastPlayerDataSource);
        assertSame(registry.testPlayerNameHistoryRepository(), registry.getPlayerNameHistoryRepository());
        assertSame(registry.testServiceOrmContext(), registry.getServiceORM());
        assertSame(registry.testNetworkServiceRepository(), registry.getNetworkServiceRepository());
        assertSame(registry.testServiceInstanceRepository(), registry.getServiceInstanceRepository());
        assertTrue(registry.isInitialized());
    }

    @Test
    void initializeRegistersOnlyEnabledBuiltInEntities() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = mock(PlayerRepository.class);
        DataRegistrySettings settings = DataRegistrySettings.builder()
                .enabledFeatures(Set.of(DataRegistryFeature.CONNECTION_INFO))
                .build();

        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));

        TestableDataRegistry registry = new TestableDataRegistry(
                logger,
                "DataRegistry",
                api,
                ormContext,
                repository,
                settings
        );

        assertTrue(registry.initialize());
        assertEquals(2, registry.lastPlayerEntityClasses.length);
        assertTrue(Arrays.asList(registry.lastPlayerEntityClasses).contains(PlayerEntity.class));
        assertTrue(Arrays.asList(registry.lastPlayerEntityClasses).contains(PlayerConnectionInfoEntity.class));
        assertFalse(Arrays.asList(registry.lastPlayerEntityClasses).contains(PlayerNameHistoryEntity.class));
        assertEquals(0, registry.lastServiceEntityClasses.length);
        assertFalse(registry.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertFalse(registry.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
    }

    @Test
    void initializeReturnsFalseWhenProviderCannotBeResolved() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.empty());

        DataRegistry registry = new DataRegistry(logger, "DataRegistry", api);
        assertFalse(registry.initialize());
        assertFalse(registry.isInitialized());
        verify(api).unregisterAllDatabasesForPlugin();
    }

    @Test
    void initializeReturnsFalseWhenProviderIsDisconnected() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(false);

        DataRegistry registry = new DataRegistry(logger, "DataRegistry", api);

        assertFalse(registry.initialize());
        verify(logger).error(org.mockito.ArgumentMatchers.eq("Failed to initialize DataRegistry."), any(Exception.class));
        verify(api).unregisterAllDatabasesForPlugin();
    }

    @Test
    void initializeReturnsFalseWhenProviderHasNoDataSource() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.empty());

        DataRegistry registry = new DataRegistry(logger, "DataRegistry", api);

        assertFalse(registry.initialize());
        verify(api).unregisterAllDatabasesForPlugin();
    }

    @Test
    void initializeIsIdempotentOnceOrmAndRepositoryExist() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = mock(PlayerRepository.class);

        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));

        TestableDataRegistry registry = new TestableDataRegistry(logger, "DataRegistry", api, ormContext, repository);

        assertTrue(registry.initialize());
        assertTrue(registry.initialize());
        verify(api).registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class);
        verify(logger).warn("DataRegistry is already initialized.");
    }

    @Test
    void initializeCleansUpPartiallyInitializedStateBeforeRetrying() throws Exception {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext staleOrm = mock(ORMContext.class);
        ORMContext freshOrm = mock(ORMContext.class);
        PlayerRepository repository = mock(PlayerRepository.class);

        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));

        TestableDataRegistry registry = new TestableDataRegistry(logger, "DataRegistry", api, freshOrm, repository);
        setField(registry, "ormContext", staleOrm);
        setField(registry, "playerRepository", null);

        assertTrue(registry.initialize());
        verify(logger).warn("Detected partially initialized DataRegistry state; forcing cleanup.");
        verify(staleOrm).shutdown();
        verify(api).unregisterAllDatabasesForPlugin();
    }

    @Test
    void initializeUsesConfiguredDatabaseConnectionId() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = mock(PlayerRepository.class);
        DataRegistrySettings settings = DataRegistrySettings.builder()
                .playerDatabaseConnectionId("custom_player_rw")
                .serviceDatabaseConnectionId("custom_player_rw")
                .ormSchemaMode("update")
                .build();

        when(api.registerDatabaseAs(DatabaseType.MYSQL, "custom_player_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));

        TestableDataRegistry registry = new TestableDataRegistry(
                logger,
                "DataRegistry",
                api,
                ormContext,
                repository,
                settings
        );

        assertTrue(registry.initialize());
        verify(api).registerDatabaseAs(DatabaseType.MYSQL, "custom_player_rw", RelationalDatabaseProvider.class);
        assertEquals("update", registry.getSettings().ormSchemaMode());
    }

    @Test
    void initializeReturnsFalseAndCleansUpWhenSetupThrows() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext ormContext = mock(ORMContext.class);

        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));

        DataRegistry registry = new FailingRepositoryDataRegistry(logger, "DataRegistry", api, ormContext);

        assertFalse(registry.initialize());
        verify(ormContext).shutdown();
        verify(api).unregisterAllDatabasesForPlugin();
        verify(logger).error(org.mockito.ArgumentMatchers.eq("Failed to initialize DataRegistry."), any(Exception.class));
    }

    @Test
    void gettersThrowWhenRegistryIsNotInitialized() {
        DataRegistry registry = new DataRegistry(mock(ILoggerAdapter.class), "DataRegistry", mock(DataProviderAPI.class));

        assertThrows(IllegalStateException.class, registry::getORM);
        assertThrows(IllegalStateException.class, registry::getPlayerRepository);
        assertThrows(IllegalStateException.class, registry::getPlayerNameHistoryRepository);
        assertFalse(registry.isInitialized());
    }

    @Test
    void newServiceRegistryServiceReflectsFeatureToggleState() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);

        DataRegistry enabledRegistry = new DataRegistry(logger, "DataRegistry", api);
        ServiceRegistryService enabledService = enabledRegistry.newServiceRegistryService();
        assertTrue(enabledService.isFeatureEnabled());

        DataRegistrySettings disabledSettings = DataRegistrySettings.builder()
                .enabledFeatures(Set.of(
                        DataRegistryFeature.ONLINE_STATUS,
                        DataRegistryFeature.CONNECTION_INFO,
                        DataRegistryFeature.SESSIONS,
                        DataRegistryFeature.NAME_HISTORY
                ))
                .build();
        DataRegistry disabledRegistry = new DataRegistry(logger, "DataRegistry", api, disabledSettings);
        ServiceRegistryService disabledService = disabledRegistry.newServiceRegistryService();
        assertFalse(disabledService.isFeatureEnabled());
    }

    @Test
    void shutdownClosesOrmAndUnregistersAllPluginDatabases() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = mock(PlayerRepository.class);

        when(api.registerDatabaseAs(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class))
                .thenReturn(Optional.of(provider));
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));

        TestableDataRegistry registry = new TestableDataRegistry(logger, "DataRegistry", api, ormContext, repository);
        assertTrue(registry.initialize());

        registry.shutdown();

        verify(ormContext).shutdown();
        verify(registry.testServiceOrmContext()).shutdown();
        verify(api).unregisterAllDatabasesForPlugin();
        assertFalse(registry.isInitialized());
    }

    @Test
    void shutdownClearsStaleRepositoryReferenceWhenOrmContextIsMissing() throws Exception {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataRegistry registry = new DataRegistry(logger, "DataRegistry", api);
        setField(registry, "playerRepository", mock(PlayerRepository.class));
        setField(registry, "ormContext", null);

        registry.shutdown();

        assertThrows(IllegalStateException.class, registry::getPlayerRepository);
        assertFalse(registry.isInitialized());
        verify(api).unregisterAllDatabasesForPlugin();
    }

    @Test
    void shutdownContinuesWhenOrmOrApiCleanupThrows() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataRegistry registry = new DataRegistry(logger, "DataRegistry", api);

        doThrow(new RuntimeException("api cleanup failed")).when(api).unregisterAllDatabasesForPlugin();
        assertDoesNotThrow(registry::shutdown);
        verify(logger).warn(
                org.mockito.ArgumentMatchers.eq("Failed to unregister DataProvider plugin-scoped databases."),
                org.mockito.ArgumentMatchers.any(RuntimeException.class)
        );
    }

    @Test
    void dataProviderLoggerAdapterMapsLevelsToPlatformLogger() throws Exception {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = new DataRegistry(logger, "DataRegistry", mock(DataProviderAPI.class));

        Field ormLoggerField = DataRegistry.class.getDeclaredField("ormLogger");
        ormLoggerField.setAccessible(true);
        Object ormLogger = ormLoggerField.get(registry);

        Method logMethod = ormLogger.getClass().getDeclaredMethod("log", LogLevel.class, String.class, Throwable.class);
        logMethod.setAccessible(true);

        RuntimeException problem = new RuntimeException("boom");
        logMethod.invoke(ormLogger, LogLevel.INFO, "i1", null);
        logMethod.invoke(ormLogger, LogLevel.INFO, "i2", problem);
        logMethod.invoke(ormLogger, LogLevel.WARN, "w1", null);
        logMethod.invoke(ormLogger, LogLevel.WARN, "w2", problem);
        logMethod.invoke(ormLogger, LogLevel.ERROR, "e1", null);
        logMethod.invoke(ormLogger, LogLevel.ERROR, "e2", problem);
        logMethod.invoke(ormLogger, null, "fallback", problem);

        verify(logger).info("i1");
        verify(logger).info("i2", problem);
        verify(logger).warn("w1");
        verify(logger).warn("w2", problem);
        verify(logger).error("e1");
        verify(logger).error("e2", problem);
        verify(logger).warn("fallback", problem);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = DataRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestableDataRegistry extends DataRegistry {
        private final ORMContext playerOrmContext;
        private final ORMContext serviceOrmContext;
        private final PlayerRepository playerRepository;
        private final PlayerNameHistoryRepository playerNameHistoryRepository;
        private final NetworkServiceRepository networkServiceRepository;
        private final ServiceInstanceRepository serviceInstanceRepository;
        private DataSource lastPlayerDataSource;
        private DataSource lastServiceDataSource;
        private Class<?>[] lastPlayerEntityClasses = new Class<?>[0];
        private Class<?>[] lastServiceEntityClasses = new Class<?>[0];

        private TestableDataRegistry(
                ILoggerAdapter logger,
                String pluginName,
                DataProviderAPI dataProviderAPI,
                ORMContext ormContext,
                PlayerRepository repository
        ) {
            super(logger, pluginName, dataProviderAPI);
            this.playerOrmContext = ormContext;
            this.playerRepository = repository;
            this.serviceOrmContext = mock(ORMContext.class);
            this.playerNameHistoryRepository = mock(PlayerNameHistoryRepository.class);
            this.networkServiceRepository = mock(NetworkServiceRepository.class);
            this.serviceInstanceRepository = mock(ServiceInstanceRepository.class);
        }

        private TestableDataRegistry(
                ILoggerAdapter logger,
                String pluginName,
                DataProviderAPI dataProviderAPI,
                ORMContext ormContext,
                PlayerRepository repository,
                DataRegistrySettings settings
        ) {
            super(logger, pluginName, dataProviderAPI, settings);
            this.playerOrmContext = ormContext;
            this.playerRepository = repository;
            this.serviceOrmContext = mock(ORMContext.class);
            this.playerNameHistoryRepository = mock(PlayerNameHistoryRepository.class);
            this.networkServiceRepository = mock(NetworkServiceRepository.class);
            this.serviceInstanceRepository = mock(ServiceInstanceRepository.class);
        }

        @Override
        ORMContext newOrmContext(DataSource dataSource, Class<?>... entityClasses) {
            this.lastPlayerDataSource = dataSource;
            this.lastPlayerEntityClasses = entityClasses;
            return playerOrmContext;
        }

        @Override
        ORMContext newServiceOrmContext(DataSource dataSource, Class<?>... entityClasses) {
            this.lastServiceDataSource = dataSource;
            this.lastServiceEntityClasses = entityClasses;
            return serviceOrmContext;
        }

        @Override
        PlayerRepository newPlayerRepository(ORMContext context) {
            return playerRepository;
        }

        @Override
        PlayerNameHistoryRepository newPlayerNameHistoryRepository(ORMContext context) {
            return playerNameHistoryRepository;
        }

        @Override
        NetworkServiceRepository newNetworkServiceRepository(ORMContext context) {
            return networkServiceRepository;
        }

        @Override
        ServiceInstanceRepository newServiceInstanceRepository(ORMContext context) {
            return serviceInstanceRepository;
        }

        private ORMContext testServiceOrmContext() {
            return serviceOrmContext;
        }

        private PlayerNameHistoryRepository testPlayerNameHistoryRepository() {
            return playerNameHistoryRepository;
        }

        private NetworkServiceRepository testNetworkServiceRepository() {
            return networkServiceRepository;
        }

        private ServiceInstanceRepository testServiceInstanceRepository() {
            return serviceInstanceRepository;
        }
    }

    private static final class FailingRepositoryDataRegistry extends DataRegistry {
        private final ORMContext ormContext;

        private FailingRepositoryDataRegistry(
                ILoggerAdapter logger,
                String pluginName,
                DataProviderAPI dataProviderAPI,
                ORMContext ormContext
        ) {
            super(logger, pluginName, dataProviderAPI);
            this.ormContext = ormContext;
        }

        @Override
        ORMContext newOrmContext(DataSource dataSource, Class<?>... entityClasses) {
            return ormContext;
        }

        @Override
        PlayerRepository newPlayerRepository(ORMContext context) {
            throw new RuntimeException("repository creation failed");
        }
    }

}
