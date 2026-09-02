package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.runtime.RuntimeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FakeDataRegistryApiProviderTest {

    @Test
    void defaultBuilderPublishesNoRuntimeIdentity() {
        FakeDataRegistryApiProvider provider = FakeDataRegistryApiProvider.builder().build();

        assertTrue(provider.getRuntimeIdentity().isEmpty());
        assertTrue(provider.getDataRegistry() instanceof FakeDataRegistryApi);
    }

    @Test
    void proxyAndBackendHelpersPublishTypedPhysicalIdentity() {
        var proxy = FakeDataRegistryApiProvider.proxy(" proxy-02 ").getRuntimeIdentity().orElseThrow();
        var backend = FakeDataRegistryApiProvider.backend("lobby-03").getRuntimeIdentity().orElseThrow();

        assertEquals("proxy-02", proxy.serviceName());
        assertEquals(RuntimeKind.PROXY, proxy.kind());
        assertEquals("lobby-03", backend.serviceName());
        assertEquals(RuntimeKind.BACKEND, backend.kind());
    }

    @Test
    void builderPreservesSuppliedApiAndCanRemoveIdentity() {
        DataRegistryApi api = mock(DataRegistryApi.class);
        FakeDataRegistryApiProvider provider = FakeDataRegistryApiProvider.builder()
                .dataRegistry(api)
                .proxy("proxy-01")
                .withoutRuntimeIdentity()
                .build();

        assertSame(api, provider.getDataRegistry());
        assertTrue(provider.getRuntimeIdentity().isEmpty());
    }
}
