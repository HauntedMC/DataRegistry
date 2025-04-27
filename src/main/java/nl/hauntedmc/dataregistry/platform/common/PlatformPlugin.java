package nl.hauntedmc.dataregistry.platform.common;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

public interface PlatformPlugin {
    DataRegistry getDataRegistry();
    ILoggerAdapter getPlatformLogger();
}
