package nl.hauntedmc.dataregistry.testkit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FakeFeatureServiceDirectoryTest {

    @Test
    void nullHandlingMatchesRuntimeDirectoryContract() {
        FakeFeatureServiceDirectory services = new FakeFeatureServiceDirectory();
        Runnable runnable = () -> { };

        assertThrows(NullPointerException.class, () -> services.contains(null));
        assertThrows(NullPointerException.class, () -> services.describe(null));
        assertThrows(NullPointerException.class, () -> services.unregister(null, runnable));
        assertThrows(NullPointerException.class, () -> services.unregister(Runnable.class, null));
    }

    @Test
    void listOrderingMatchesRuntimeOwnerFeatureAndApiOrdering() {
        FakeFeatureServiceDirectory services = new FakeFeatureServiceDirectory();
        Runnable runnable = () -> { };
        Callable<String> callable = () -> "ok";
        AutoCloseable closeable = () -> { };

        services.register("Zoo", "Alpha", Runnable.class, runnable);
        services.register("alpha", "Zulu", Callable.class, callable);
        services.register("Alpha", "beta", AutoCloseable.class, closeable);

        assertEquals(
                List.of(AutoCloseable.class, Callable.class, Runnable.class),
                services.list().stream().map(info -> info.apiType()).toList()
        );
    }
}
