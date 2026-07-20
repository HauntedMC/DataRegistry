package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;

import java.util.Objects;
import java.util.Set;

/**
 * Small configurable {@link DataRegistryApi} fake for feature contract tests.
 */
public final class FakeDataRegistryApi implements DataRegistryApi {

    private final PlayerData players;
    private final FeatureServiceDirectory featureServices;
    private final Set<DataRegistryFeature> enabledFeatures;
    private final boolean ready;

    public FakeDataRegistryApi(
            PlayerData players,
            FeatureServiceDirectory featureServices,
            Set<DataRegistryFeature> enabledFeatures,
            boolean ready
    ) {
        this.players = Objects.requireNonNull(players, "players must not be null");
        this.featureServices = Objects.requireNonNull(featureServices, "featureServices must not be null");
        this.enabledFeatures = Set.copyOf(Objects.requireNonNull(enabledFeatures, "enabledFeatures must not be null"));
        this.ready = ready;
    }

    @Override
    public PlayerData players() {
        return players;
    }

    @Override
    public FeatureServiceDirectory featureServices() {
        return featureServices;
    }

    @Override
    public Set<DataRegistryFeature> enabledFeatures() {
        return enabledFeatures;
    }

    @Override
    public boolean supports(DataRegistryFeature feature) {
        return enabledFeatures.contains(feature);
    }

    @Override
    public boolean isReady() {
        return ready;
    }
}
