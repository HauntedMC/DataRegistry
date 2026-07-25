package nl.hauntedmc.dataregistry.acceptance.paper;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Exercises a bundled Paper artifact exclusively through the public DataRegistry API. */
public final class PaperAcceptanceConsumer extends JavaPlugin {

    private static final UUID PLAYER_UUID = UUID.fromString("8a1c5035-c774-405e-ae4a-0948f0595d12");

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                DataRegistryApi api = resolveApi();
                require(api.isReady(), "DataRegistry API is not ready.");
                require(api.supports(DataRegistryFeature.LANGUAGE), "Language support is unexpectedly disabled.");
                require(api.supports(DataRegistryFeature.NICKNAMES), "Nickname support is unexpectedly disabled.");
                PlayerIdentity identity = api.players().findIdentity(PLAYER_UUID)
                        .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
                api.players().saveLanguage(identity.playerId(), "NL", "nl")
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                api.players().saveNickname(identity.playerId(), "Paper Registry Tester")
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                require("NL".equals(api.players().findLanguage(identity.playerId())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().language()),
                        "Language preference did not round-trip through the public API.");
                require("Paper Registry Tester".equals(api.players().findNickname(identity.playerId())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow()),
                        "Nickname preference did not round-trip through the public API.");
                CompletableFuture<Boolean> reload = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(this, () -> reload.complete(Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(), "dataprovider reload"
                )));
                require(reload.get(10, TimeUnit.SECONDS), "DataProvider reload command was rejected.");
                require("Paper Registry Tester".equals(api.players().findNickname(identity.playerId())
                        .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow()),
                        "DataRegistry query did not survive DataProvider configuration reload.");
                getLogger().info("DATAREGISTRY_ACCEPTANCE_PASS platform=paper");
            } catch (Exception exception) {
                getLogger().severe("DATAREGISTRY_ACCEPTANCE_FAIL platform=paper cause=" + exception);
            }
        });
    }

    private DataRegistryApi resolveApi() {
        Plugin plugin = getServer().getPluginManager().getPlugin("DataRegistry");
        if (!(plugin instanceof DataRegistryApiProvider provider)) {
            throw new IllegalStateException("DataRegistry plugin does not expose DataRegistryApiProvider.");
        }
        return provider.getDataRegistry();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
