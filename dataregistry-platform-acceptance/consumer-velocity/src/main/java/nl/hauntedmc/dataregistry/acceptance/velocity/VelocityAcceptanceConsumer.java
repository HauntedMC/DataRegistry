package nl.hauntedmc.dataregistry.acceptance.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.runtime.RuntimeKind;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Exercises a bundled Velocity artifact exclusively through the public DataRegistry API. */
@Plugin(
        id = "dataregistry-acceptance",
        name = "DataRegistry Acceptance",
        version = "${project.version}",
        dependencies = @Dependency(id = "dataregistry")
)
public final class VelocityAcceptanceConsumer {

    private static final UUID PLAYER_UUID = UUID.fromString("8a1c5035-c774-405e-ae4a-0948f0595d12");

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public VelocityAcceptanceConsumer(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getScheduler().buildTask(this, () -> {
            try {
                DataRegistryApiProvider provider = resolveProvider();
                var runtimeIdentity = provider.getRuntimeIdentity().orElseThrow(() ->
                        new IllegalStateException("Velocity runtime identity is unavailable."));
                require("acceptance-proxy".equals(runtimeIdentity.serviceName()),
                        "Velocity runtime identity did not use configured service-name.");
                require(runtimeIdentity.kind() == RuntimeKind.PROXY,
                        "Velocity runtime identity kind is not PROXY.");

                DataRegistryApi api = provider.getDataRegistry();
                require(api.isReady(), "DataRegistry API is not ready.");
                require(api.supports(DataRegistryFeature.LANGUAGE), "Language support is unexpectedly disabled.");
                PlayerIdentity identity = api.players().findIdentity(PLAYER_UUID)
                        .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
                api.players().saveLanguage(identity.playerId(), "EN", "en")
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                api.players().saveNickname(identity.playerId(), "Velocity Registry Tester")
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                require("EN".equals(api.players().findLanguage(identity.playerId())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().language()),
                        "Language preference did not round-trip through the public API.");
                require("Velocity Registry Tester".equals(api.players().findNickname(identity.playerId())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow()),
                        "Nickname preference did not round-trip through the public API.");
                require(proxy.getCommandManager().executeAsync(
                        proxy.getConsoleCommandSource(), "dataproviderproxy reload"
                ).get(10, TimeUnit.SECONDS), "DataProvider reload command was rejected.");
                require("Velocity Registry Tester".equals(api.players().findNickname(identity.playerId())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow()),
                        "DataRegistry query did not survive DataProvider configuration reload.");
                logger.info("DATAREGISTRY_ACCEPTANCE_PASS platform=velocity");
            } catch (Exception exception) {
                logger.error("DATAREGISTRY_ACCEPTANCE_FAIL platform=velocity", exception);
            }
        }).schedule();
    }

    private DataRegistryApiProvider resolveProvider() {
        Object plugin = proxy.getPluginManager().getPlugin("dataregistry")
                .flatMap(container -> container.getInstance())
                .orElseThrow(() -> new IllegalStateException("DataRegistry plugin instance is unavailable."));
        if (!(plugin instanceof DataRegistryApiProvider provider)) {
            throw new IllegalStateException("DataRegistry plugin does not expose DataRegistryApiProvider.");
        }
        return provider;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
