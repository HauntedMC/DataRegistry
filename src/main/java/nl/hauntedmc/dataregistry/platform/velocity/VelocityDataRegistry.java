package nl.hauntedmc.dataregistry.platform.velocity;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataprovider.platform.velocity.VelocityDataProvider;
import nl.hauntedmc.dataregistry.platform.common.PlatformPlugin;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerService;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.platform.common.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.velocity.listener.PlayerStatusListener;
import nl.hauntedmc.dataregistry.platform.velocity.logger.SLF4JLoggerAdapter;
import nl.hauntedmc.dataregistry.platform.velocity.util.VelocityPlayerAdapter;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "dataregistry",
        name = "DataRegistry",
        version = "1.2.0",
        description = "DataRegistry for cross-platform data handling.",
        authors = {"HauntedMC"},
        dependencies = {
                @Dependency(id = "dataprovider")
        }
)
public class VelocityDataRegistry implements PlatformPlugin {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private static DataRegistry dataRegistry;

    @Inject
    private Injector injector;
    private SLF4JLoggerAdapter logInstance;

    @Inject
    public VelocityDataRegistry(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        VelocityPlayerAdapter.setProxy(proxyServer);

        DataProviderAPI dataProviderAPI = VelocityDataProvider.getDataProviderAPI();
        logInstance = new SLF4JLoggerAdapter(logger);

        dataRegistry = new DataRegistry(logInstance,"DataRegistry", dataProviderAPI);
        if (!dataRegistry.initialize()) {
            logger.error("Database connection not established, disabling plugin.");
            return;
        }

        PlayerService playerService = new PlayerService(this);
        PlayerStatusService statusService = new PlayerStatusService(this);
        PlayerConnectionInfoService connectionInfoService = new PlayerConnectionInfoService(this);
        PlayerSessionService sessionService = new PlayerSessionService(this);

        proxyServer.getEventManager().register(this,
                new PlayerStatusListener(playerService, statusService, connectionInfoService, sessionService));

        logger.info("DataRegistry enabled successfully on Velocity.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (dataRegistry != null) {
            dataRegistry.shutdown();
        }
        logger.info("DataRegistry disabled on Velocity.");
    }

    public DataRegistry getDataRegistry() {
        return dataRegistry;
    }

    @Override
    public ILoggerAdapter getPlatformLogger() {
        return logInstance;
    }

    public static PlayerRepository getPlayerRepository() {
        return dataRegistry != null ? dataRegistry.getPlayerRepository() : null;
    }
}
