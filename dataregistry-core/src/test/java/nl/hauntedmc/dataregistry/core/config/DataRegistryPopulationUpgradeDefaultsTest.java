package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DataRegistryPopulationUpgradeDefaultsTest {

    @Test
    void omittedPopulationPreservesExplicitPrerequisiteOptOut() {
        DataRegistrySettings settings = new DataRegistrySettingsLoader().parse(
                Map.of("features", Map.of("sessions", false)),
                mock(ILoggerAdapter.class)
        );

        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
    }

    @Test
    void explicitPopulationStillEnablesRequiredDomains() {
        DataRegistrySettings settings = new DataRegistrySettingsLoader().parse(
                Map.of("features", Map.of(
                        "online-status", false,
                        "sessions", false,
                        "session-visits", false,
                        "population", true
                )),
                mock(ILoggerAdapter.class)
        );

        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
    }
}
