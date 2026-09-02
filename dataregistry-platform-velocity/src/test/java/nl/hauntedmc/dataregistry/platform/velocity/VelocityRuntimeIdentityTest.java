package nl.hauntedmc.dataregistry.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.runtime.RuntimeKind;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class VelocityRuntimeIdentityTest {

    @Test
    void configuredVelocityServiceNamePublishesProxyIdentityWhenRuntimeIsActive() throws Exception {
        ActiveVelocityDataRegistry plugin = new ActiveVelocityDataRegistry();
        setSettings(plugin, DataRegistrySettings.builder().velocityServiceName(" proxy-eu-02 ").build());

        var identity = plugin.getRuntimeIdentity().orElseThrow();

        assertEquals("proxy-eu-02", identity.serviceName());
        assertEquals(RuntimeKind.PROXY, identity.kind());
    }

    @Test
    void inactiveVelocityRuntimeDoesNotLeakDefaultTestIdentity() {
        VelocityDataRegistry plugin = new VelocityDataRegistry(
                mock(ProxyServer.class),
                mock(Logger.class),
                Path.of("target", "test-data", "velocity-runtime-identity")
        );

        assertTrue(plugin.getRuntimeIdentity().isEmpty());
    }

    private static void setSettings(VelocityDataRegistry plugin, DataRegistrySettings settings) throws Exception {
        Field field = VelocityDataRegistry.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(plugin, settings);
    }

    private static final class ActiveVelocityDataRegistry extends VelocityDataRegistry {
        private final DataRegistryApi api = mock(DataRegistryApi.class);

        private ActiveVelocityDataRegistry() {
            super(
                    mock(ProxyServer.class),
                    mock(Logger.class),
                    Path.of("target", "test-data", "velocity-runtime-identity-active")
            );
        }

        @Override
        public DataRegistryApi getDataRegistry() {
            return api;
        }
    }
}
