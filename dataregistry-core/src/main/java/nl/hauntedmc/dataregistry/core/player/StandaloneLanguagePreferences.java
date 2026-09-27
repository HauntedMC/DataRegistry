package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageMutationResult;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageSettings;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Platform-neutral owner service for website language operations. */
public final class StandaloneLanguagePreferences implements AutoCloseable {
    private final DataRegistry registry;

    private StandaloneLanguagePreferences(DataRegistry registry) { this.registry = registry; }

    public static StandaloneLanguagePreferences open(DataProviderAPI provider, ILoggerAdapter logger) {
        DataRegistrySettings settings = DataRegistrySettings.builder()
                .enabledFeatures(Set.of(DataRegistryFeature.LANGUAGE))
                .ormSchemaMode("validate")
                .build();
        DataRegistry registry = new DataRegistry(logger, "WebAppLanguage", provider, settings, false);
        if (!registry.initialize()) {
            registry.shutdown();
            throw new IllegalStateException("DataRegistry language owner is unavailable");
        }
        return new StandaloneLanguagePreferences(registry);
    }

    public CompletionStage<Optional<PlayerLanguageSettings>> find(long playerId, UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return registry.players().findLanguageForIdentity(playerId, uuid);
    }

    public CompletionStage<PlayerLanguageMutationResult> save(long playerId, UUID uuid, String preference,
                                                               long expectedVersion, UUID requestId) {
        return registry.players().saveLanguagePreference(playerId, uuid, preference, expectedVersion, requestId);
    }

    public List<PlayerDataChange> pendingChanges(int limit) { return registry.pendingPlayerDataChanges(limit); }

    public void markPublished(UUID eventId) { registry.markPlayerDataChangePublished(eventId); }

    public int purgeHistory(Duration retention, int batchSize) {
        return registry.purgePlayerDataHistory(retention, batchSize);
    }

    @Override public void close() { registry.shutdown(); }
}
