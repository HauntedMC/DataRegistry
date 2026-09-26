package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerProfile;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** A non-authoritative, read-only projection suitable for a non-Minecraft process. */
public final class ReadOnlyPlayerProfiles implements AutoCloseable {
    private final DataRegistry registry;

    private ReadOnlyPlayerProfiles(DataRegistry registry) {
        this.registry = registry;
    }

    public static ReadOnlyPlayerProfiles open(DataProviderAPI provider, ILoggerAdapter logger) {
        DataRegistrySettings settings = DataRegistrySettings.builder()
                .enabledFeatures(Set.of(DataRegistryFeature.ACTIVITY_SUMMARY,
                        DataRegistryFeature.SESSIONS, DataRegistryFeature.PLAYTIME))
                .ormSchemaMode("validate")
                .build();
        DataRegistry registry = new DataRegistry(logger, "WebApp", provider, settings, false);
        if (!registry.initialize()) {
            registry.shutdown();
            throw new IllegalStateException("DataRegistry player profile read model is unavailable.");
        }
        return new ReadOnlyPlayerProfiles(registry);
    }

    /** Checks the numeric ID and UUID together before returning an allowlisted profile. */
    public CompletionStage<Result> find(long playerId, UUID uuid) {
        if (playerId <= 0L) throw new IllegalArgumentException("playerId must be positive.");
        Objects.requireNonNull(uuid, "uuid");
        return registry.players().findIdentity(playerId).thenCompose(found -> {
            if (found.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(Result.missing());
            PlayerIdentity identity = found.get();
            if (!identity.uuid().equals(uuid)) {
                return java.util.concurrent.CompletableFuture.completedFuture(Result.stale());
            }
            return registry.players().findProfile(identity, 0).thenApply(profile ->
                    profile.map(value -> Result.found(summary(value))).orElseGet(Result::missing));
        });
    }

    static Summary summary(PlayerProfile profile) {
        var playtime = profile.playtime().orElse(null);
        List<Gamemode> gamemodes = playtime == null ? List.of() : playtime.gamemodes().stream()
                .filter(item -> item.trackedMillis() > 0)
                .limit(10)
                .map(item -> new Gamemode(item.gamemodeKey(), item.trackedMillis()))
                .toList();
        return new Summary(profile.identity().username(),
                profile.activity().map(activity -> activity.firstSeenAt()).orElse(null),
                playtime == null ? null : playtime.networkTotalMillis(),
                gamemodes, playtime == null ? null : playtime.generatedAt());
    }

    @Override public void close() {
        registry.shutdown();
    }

    public enum Status { FOUND, MISSING, STALE }
    public record Result(Status status, Summary summary) {
        public static Result found(Summary value) { return new Result(Status.FOUND, value); }
        public static Result missing() { return new Result(Status.MISSING, null); }
        public static Result stale() { return new Result(Status.STALE, null); }
    }
    public record Summary(String name, Instant firstSeenAt, Long networkTotalMillis,
                          List<Gamemode> gamemodes, Instant playtimeGeneratedAt) { }
    public record Gamemode(String key, long trackedMillis) { }
}
