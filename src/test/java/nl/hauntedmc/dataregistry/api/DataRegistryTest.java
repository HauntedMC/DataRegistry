package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.logging.LogLevel;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.backend.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

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
        assertSame(dataSource, registry.lastDataSource);
        assertTrue(registry.isInitialized());
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
        verify(logger).error("Database provider 'player_data_rw' is not connected.");
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
                .databaseConnectionId("custom_player_rw")
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
        assertFalse(registry.isInitialized());
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
        private final ORMContext ormContext;
        private final PlayerRepository repository;
        private DataSource lastDataSource;

        private TestableDataRegistry(
                ILoggerAdapter logger,
                String pluginName,
                DataProviderAPI dataProviderAPI,
                ORMContext ormContext,
                PlayerRepository repository
        ) {
            super(logger, pluginName, dataProviderAPI);
            this.ormContext = ormContext;
            this.repository = repository;
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
            this.ormContext = ormContext;
            this.repository = repository;
        }

        @Override
        ORMContext newOrmContext(DataSource dataSource, Class<?>... entityClasses) {
            this.lastDataSource = dataSource;
            return ormContext;
        }

        @Override
        PlayerRepository newPlayerRepository(ORMContext context) {
            return repository;
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
