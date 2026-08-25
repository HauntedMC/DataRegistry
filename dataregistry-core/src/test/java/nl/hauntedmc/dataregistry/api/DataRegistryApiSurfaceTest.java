package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataregistry.api.observation.DataRegistryInstrumentation;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class DataRegistryApiSurfaceTest {

    @Test
    void publicApiContainsOnlyDomainFacadesAndCapabilities() {
        Set<String> methodNames = Set.of(
                "players",
                "population",
                "featureServices",
                "enabledFeatures",
                "supports",
                "isReady"
        );

        for (Method method : DataRegistryApi.class.getDeclaredMethods()) {
            assertFalse(method.getReturnType().getName().contains("repository"));
            assertFalse(method.getReturnType().getName().contains("entities"));
            assertFalse(method.getReturnType().getName().contains("ORM"));
            assertFalse(method.getReturnType().getName().contains("dataprovider"));
            assertFalse(method.getReturnType().getName().contains("velocity"));
            assertFalse(method.getReturnType().getName().contains("bukkit"));
        }

        assertEquals(methodNames, Set.of(DataRegistryApi.class.getDeclaredMethods())
                .stream()
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @Test
    void platformPublishesTheNarrowApiAndSeparateInstrumentationCapability() throws NoSuchMethodException {
        assertEquals(DataRegistryApi.class, DataRegistryApiProvider.class.getMethod("getDataRegistry").getReturnType());
        assertEquals(DataRegistryApi.class, PlatformPlugin.class.getMethod("getDataRegistry").getReturnType());
        assertEquals(
                DataRegistryInstrumentation.class,
                DataRegistryApiProvider.class.getMethod("getDataRegistryInstrumentation").getReturnType()
        );
        assertEquals(
                DataRegistryInstrumentation.class,
                PlatformPlugin.class.getMethod("getDataRegistryInstrumentation").getReturnType()
        );
    }

    @Test
    void platformInstrumentationResolvesTheActiveRuntimeCapability() {
        DataRegistryApi api = mock(
                DataRegistryApi.class,
                withSettings().extraInterfaces(DataRegistryInstrumentation.class)
        );
        PlatformPlugin plugin = new PlatformPlugin() {
            @Override
            public DataRegistryApi getDataRegistry() {
                return api;
            }

            @Override
            public ILoggerAdapter getPlatformLogger() {
                return null;
            }
        };

        assertSame((DataRegistryInstrumentation) api, plugin.getDataRegistryInstrumentation());
    }
}
