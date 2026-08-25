package nl.hauntedmc.dataregistry.platform.common;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryInstrumentation;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

public interface PlatformPlugin extends DataRegistryApiProvider {

    @Override
    default DataRegistryInstrumentation getDataRegistryInstrumentation() {
        DataRegistryApi api = getDataRegistry();
        return api instanceof DataRegistryInstrumentation instrumentation
                ? instrumentation
                : DataRegistryInstrumentation.noop();
    }

    ILoggerAdapter getPlatformLogger();
}
