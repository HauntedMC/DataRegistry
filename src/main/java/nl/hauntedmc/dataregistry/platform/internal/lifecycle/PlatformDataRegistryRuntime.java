package nl.hauntedmc.dataregistry.platform.internal.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Thread-safe runtime holder for the active platform DataRegistry instance.
 */
public final class PlatformDataRegistryRuntime {

    private static final String NOT_INITIALIZED_MESSAGE = "DataRegistry is not initialized yet.";
    private static final String LEFTOVER_INSTANCE_MESSAGE =
            "Detected leftover DataRegistry instance during enable; forcing cleanup first.";
    private static final String LEFTOVER_SHUTDOWN_FAILURE_MESSAGE =
            "Failed to shut down leftover DataRegistry instance cleanly.";
    private static final String STARTUP_INITIALIZATION_FAILURE_MESSAGE =
            "Failed to complete DataRegistry startup initialization.";
    private static final String STARTUP_FAILURE_SHUTDOWN_MESSAGE =
            "Failed to shut down DataRegistry instance after startup initialization failure.";
    private static final String SHUTDOWN_FAILURE_MESSAGE =
            "Failed to shut down DataRegistry cleanly.";

    private DataRegistry activeRegistry;

    /**
     * Starts a fresh DataRegistry instance.
     * If an old runtime instance is still present, it is shut down first.
     */
    public synchronized DataRegistry start(
            Supplier<DataRegistry> registryFactory,
            Consumer<DataRegistry> startupInitializer,
            ILoggerAdapter logger
    ) {
        Objects.requireNonNull(registryFactory, "Registry factory cannot be null.");
        Objects.requireNonNull(startupInitializer, "Startup initializer cannot be null.");
        Objects.requireNonNull(logger, "Logger cannot be null.");

        DataRegistry previousRegistry = activeRegistry;
        activeRegistry = null;
        if (previousRegistry != null) {
            logger.warn(LEFTOVER_INSTANCE_MESSAGE);
            shutdownRegistry(previousRegistry, logger, LEFTOVER_SHUTDOWN_FAILURE_MESSAGE);
        }

        DataRegistry createdRegistry = Objects.requireNonNull(
                registryFactory.get(),
                "Registry factory cannot return null."
        );

        try {
            startupInitializer.accept(createdRegistry);
        } catch (RuntimeException | Error exception) {
            logger.error(STARTUP_INITIALIZATION_FAILURE_MESSAGE, exception);
            shutdownRegistry(createdRegistry, logger, STARTUP_FAILURE_SHUTDOWN_MESSAGE);
            throw exception;
        }

        activeRegistry = createdRegistry;
        return createdRegistry;
    }

    /**
     * Stops the current DataRegistry instance, if one is active.
     */
    public synchronized void stop(ILoggerAdapter logger) {
        Objects.requireNonNull(logger, "Logger cannot be null.");
        DataRegistry registryToShutdown = activeRegistry;
        activeRegistry = null;
        if (registryToShutdown != null) {
            shutdownRegistry(registryToShutdown, logger, SHUTDOWN_FAILURE_MESSAGE);
        }
    }

    /**
     * Resolves the active DataRegistry instance.
     */
    public synchronized DataRegistry getDataRegistry() {
        DataRegistry registry = activeRegistry;
        if (registry == null) {
            throw new IllegalStateException(NOT_INITIALIZED_MESSAGE);
        }
        return registry;
    }

    private static void shutdownRegistry(DataRegistry registry, ILoggerAdapter logger, String failureMessage) {
        try {
            registry.shutdown();
        } catch (Exception e) {
            logger.error(failureMessage, e);
        }
    }
}
