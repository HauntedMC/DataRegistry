package nl.hauntedmc.dataregistry.platform.internal.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PlatformDataRegistryRuntimeTest {

    @Test
    void startRejectsNullArguments() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);

        assertThrows(NullPointerException.class, () -> runtime.start(null, r -> {
        }, logger));
        assertThrows(NullPointerException.class, () -> runtime.start(() -> registry, null, logger));
        assertThrows(NullPointerException.class, () -> runtime.start(() -> registry, r -> {
        }, null));
    }

    @Test
    void startShutsDownLeftoverRegistryBeforeReplacing() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry previousRegistry = mock(DataRegistry.class);
        DataRegistry replacementRegistry = mock(DataRegistry.class);

        runtime.start(() -> previousRegistry, registry -> {
        }, logger);
        runtime.start(() -> replacementRegistry, registry -> {
        }, logger);

        verify(logger).warn("Detected leftover DataRegistry instance during enable; forcing cleanup first.");
        verify(previousRegistry).shutdown();
    }

    @Test
    void startLogsErrorWhenLeftoverShutdownFails() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry previousRegistry = mock(DataRegistry.class);
        DataRegistry replacementRegistry = mock(DataRegistry.class);
        doThrow(new RuntimeException("boom")).when(previousRegistry).shutdown();

        runtime.start(() -> previousRegistry, registry -> {
        }, logger);
        runtime.start(() -> replacementRegistry, registry -> {
        }, logger);

        verify(logger).error(
                org.mockito.ArgumentMatchers.eq("Failed to shut down leftover DataRegistry instance cleanly."),
                org.mockito.ArgumentMatchers.any(RuntimeException.class)
        );
    }

    @Test
    void stopShutsDownActiveRegistryAndMakesItUnavailable() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);

        runtime.start(() -> registry, created -> {
        }, logger);
        runtime.stop(logger);

        verify(registry).shutdown();
        assertThrows(IllegalStateException.class, runtime::getDataRegistry);
    }

    @Test
    void stopRejectsNullLoggerAndIsNoopWhenNotStarted() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);

        assertThrows(NullPointerException.class, () -> runtime.stop(null));
        runtime.stop(logger);
        verify(logger, never()).error(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Throwable.class));
    }

    @Test
    void stopLogsErrorWhenShutdownFails() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);
        doThrow(new RuntimeException("boom")).when(registry).shutdown();

        runtime.start(() -> registry, created -> {
        }, logger);
        runtime.stop(logger);

        verify(logger).error(
                org.mockito.ArgumentMatchers.eq("Failed to shut down DataRegistry cleanly."),
                org.mockito.ArgumentMatchers.any(RuntimeException.class)
        );
    }

    @Test
    void getDataRegistryReturnsActiveRegistry() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);

        runtime.start(() -> registry, created -> {
        }, logger);

        assertSame(registry, runtime.getDataRegistry());
    }

    @Test
    void getDataRegistryThrowsWhenNotStarted() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        assertThrows(IllegalStateException.class, runtime::getDataRegistry);
    }

    @Test
    void startRollsBackRegistryWhenInitializerFails() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistry registry = mock(DataRegistry.class);

        assertThrows(
                IllegalStateException.class,
                () -> runtime.start(
                        () -> registry,
                        created -> {
                            throw new IllegalStateException("startup failed");
                        },
                        logger
                )
        );

        verify(registry).shutdown();
        assertThrows(IllegalStateException.class, runtime::getDataRegistry);
    }

    @Test
    void startRejectsNullRegistryFromFactory() {
        PlatformDataRegistryRuntime runtime = new PlatformDataRegistryRuntime();
        ILoggerAdapter logger = mock(ILoggerAdapter.class);

        assertThrows(
                NullPointerException.class,
                () -> runtime.start(() -> null, registry -> {
                }, logger)
        );
    }
}
