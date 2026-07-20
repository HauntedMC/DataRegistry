package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DataRegistryApiSurfaceTest {

    @Test
    void publicApiContainsOnlyDomainFacadesAndCapabilities() {
        Set<String> methodNames = Set.of(
                "players",
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
    void platformPublishesTheNarrowApiRatherThanTheCoreRuntime() throws NoSuchMethodException {
        assertEquals(DataRegistryApi.class, DataRegistryApiProvider.class.getMethod("getDataRegistry").getReturnType());
        assertEquals(DataRegistryApi.class, PlatformPlugin.class.getMethod("getDataRegistry").getReturnType());
    }
}
