package nl.hauntedmc.dataregistry.api.service;

import nl.hauntedmc.dataregistry.backend.service.DefaultFeatureServiceDirectory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureServiceDirectoryTest {

    @Test
    void registersAndResolvesTypedService() {
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        GreetingService service = () -> "hello";

        FeatureServiceHandle handle = directory.register("ProxyFeatures", "Greeting", GreetingService.class, service);

        assertSame(service, directory.find(GreetingService.class).orElseThrow());
        assertSame(service, directory.require(GreetingService.class));
        assertTrue(directory.contains(GreetingService.class));
        assertEquals("ProxyFeatures", handle.info().ownerPlugin());
        assertEquals("Greeting", directory.describe(GreetingService.class).orElseThrow().ownerFeature());
    }

    @Test
    void replacesExistingServiceForSameApiType() {
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        GreetingService first = () -> "first";
        GreetingService second = () -> "second";

        directory.register("ProxyFeatures", "First", GreetingService.class, first);
        directory.register("ProxyFeatures", "Second", GreetingService.class, second);

        assertSame(second, directory.find(GreetingService.class).orElseThrow());
        assertEquals("Second", directory.describe(GreetingService.class).orElseThrow().ownerFeature());
    }

    @Test
    void handleOnlyUnregistersExactServiceInstance() {
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        GreetingService first = () -> "first";
        GreetingService second = () -> "second";

        FeatureServiceHandle firstHandle = directory.register("ProxyFeatures", "First", GreetingService.class, first);
        directory.register("ProxyFeatures", "Second", GreetingService.class, second);

        firstHandle.close();

        assertSame(second, directory.find(GreetingService.class).orElseThrow());
    }

    @Test
    void unregisterOwnerRemovesOnlyMatchingOwner() {
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        GreetingService greeting = () -> "hello";
        CounterService counter = () -> 1;

        directory.register("ProxyFeatures", "Greeting", GreetingService.class, greeting);
        directory.register("ServerFeatures", "Counter", CounterService.class, counter);

        assertEquals(1, directory.unregisterOwner("ProxyFeatures", "Greeting"));

        assertTrue(directory.find(GreetingService.class).isEmpty());
        assertSame(counter, directory.find(CounterService.class).orElseThrow());
    }

    @Test
    void listReturnsStableMetadataSnapshot() {
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        directory.register("ServerFeatures", "Counter", CounterService.class, (CounterService) () -> 1);
        directory.register("ProxyFeatures", "Greeting", GreetingService.class, (GreetingService) () -> "hello");

        List<FeatureServiceInfo> services = directory.list();

        assertEquals(List.of("ProxyFeatures", "ServerFeatures"),
                services.stream().map(FeatureServiceInfo::ownerPlugin).toList());
    }

    @Test
    void rejectsInvalidRegistrationsAndRequiredMissingService() {
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();

        assertThrows(NullPointerException.class, () -> directory.register("ProxyFeatures", "Greeting", null, "x"));
        assertThrows(NullPointerException.class, () -> directory.register("ProxyFeatures", "Greeting", String.class, null));
        assertThrows(IllegalArgumentException.class, () -> directory.register(" ", "Greeting", String.class, "x"));
        assertThrows(IllegalArgumentException.class, () -> directory.register("ProxyFeatures", " ", String.class, "x"));
        assertThrows(NullPointerException.class, () -> directory.register(
                "ProxyFeatures",
                "Greeting",
                GreetingService.class,
                (GreetingService) null
        ));
        assertThrows(IllegalStateException.class, () -> directory.require(GreetingService.class));
    }

    @Test
    void exactUnregisterAndClearRemoveServices() {
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        GreetingService greeting = () -> "hello";
        CounterService counter = () -> 1;

        directory.register("ProxyFeatures", "Greeting", GreetingService.class, greeting);
        directory.register("ProxyFeatures", "Counter", CounterService.class, counter);

        assertFalse(directory.unregister(GreetingService.class, counter));
        assertTrue(directory.unregister(GreetingService.class, greeting));
        assertTrue(directory.find(GreetingService.class).isEmpty());

        directory.clear();

        assertTrue(directory.find(CounterService.class).isEmpty());
    }

    private interface GreetingService {
        String greeting();
    }

    private interface CounterService {
        int count();
    }
}
