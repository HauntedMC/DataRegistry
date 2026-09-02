package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataregistry.api.runtime.RuntimeIdentity;
import nl.hauntedmc.dataregistry.api.runtime.RuntimeKind;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RuntimeIdentityApiTest {

    @Test
    void runtimeIdentityNormalizesServiceNameAndPreservesKind() {
        RuntimeIdentity identity = new RuntimeIdentity(" proxy-01 ", RuntimeKind.PROXY);

        assertEquals("proxy-01", identity.serviceName());
        assertEquals(RuntimeKind.PROXY, identity.kind());
    }

    @Test
    void runtimeIdentityRejectsInvalidServiceNamesAndKind() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeIdentity(null, RuntimeKind.PROXY));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeIdentity("   ", RuntimeKind.PROXY));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeIdentity(
                "s".repeat(RuntimeIdentity.MAX_SERVICE_NAME_LENGTH + 1),
                RuntimeKind.BACKEND
        ));
        assertThrows(NullPointerException.class, () -> new RuntimeIdentity("lobby-01", null));
    }

    @Test
    void existingProvidersRemainCompatibleAndPublishNoIdentityByDefault() {
        DataRegistryApi api = mock(DataRegistryApi.class);
        DataRegistryApiProvider provider = () -> api;

        assertEquals(api, provider.getDataRegistry());
        assertEquals(Optional.empty(), provider.getRuntimeIdentity());
    }

    @Test
    void runtimeIdentityIsProviderMetadataNotADataRegistryDomain() {
        assertTrue(java.util.Arrays.stream(DataRegistryApi.class.getMethods())
                .noneMatch(method -> method.getName().equals("getRuntimeIdentity")
                        || method.getName().equals("runtimeIdentity")));
    }
}
