package nl.hauntedmc.dataregistry.platform.common;

import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

public interface PlatformPlugin extends DataRegistryApiProvider {

    ILoggerAdapter getPlatformLogger();
}
