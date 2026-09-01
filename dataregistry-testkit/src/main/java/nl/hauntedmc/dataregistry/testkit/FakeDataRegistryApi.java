package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.population.PopulationData;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.dataregistry.api.session.NetworkSessionApi;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Small configurable {@link DataRegistryApi} fake for feature contract tests. */
public final class FakeDataRegistryApi implements DataRegistryApi {

    private final PlayerData players;
    private final PopulationData population;
    private final NetworkSessionApi sessions;
    private final FeatureServiceDirectory featureServices;
    private final Set<DataRegistryFeature> enabledFeatures;
    private final boolean ready;

    public FakeDataRegistryApi(
            PlayerData players,
            PopulationData population,
            NetworkSessionApi sessions,
            FeatureServiceDirectory featureServices,
            Set<DataRegistryFeature> enabledFeatures,
            boolean ready
    ) {
        this.players = Objects.requireNonNull(players, "players must not be null");
        this.population = Objects.requireNonNull(population, "population must not be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.featureServices = Objects.requireNonNull(featureServices, "featureServices must not be null");
        this.enabledFeatures = Set.copyOf(Objects.requireNonNull(enabledFeatures, "enabledFeatures must not be null"));
        this.ready = ready;
    }

    /**
     * Creates a fake with in-memory Population and feature-service collaborators.
     */
    public FakeDataRegistryApi(PlayerData players, Set<DataRegistryFeature> enabledFeatures, boolean ready) {
        this(
                players,
                new FakePopulationData(),
                new FakeNetworkSessionApi(),
                new FakeFeatureServiceDirectory(),
                enabledFeatures,
                ready
        );
    }

    /**
     * Creates a fluent builder whose default collaborators are all in-memory testkit implementations.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public PlayerData players() {
        return players;
    }

    @Override
    public PopulationData population() {
        return population;
    }

    @Override
    public NetworkSessionApi sessions() {
        return sessions;
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

    public static final class Builder {
        private PlayerData players;
        private PopulationData population;
        private NetworkSessionApi sessions;
        private FeatureServiceDirectory featureServices;
        private final EnumSet<DataRegistryFeature> enabledFeatures = EnumSet.noneOf(DataRegistryFeature.class);
        private boolean ready = true;

        private Builder() {
        }

        public Builder players(PlayerData players) {
            this.players = Objects.requireNonNull(players, "players must not be null");
            return this;
        }

        public Builder population(PopulationData population) {
            this.population = Objects.requireNonNull(population, "population must not be null");
            return this;
        }

        public Builder sessions(NetworkSessionApi sessions) {
            this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
            return this;
        }

        public Builder featureServices(FeatureServiceDirectory featureServices) {
            this.featureServices = Objects.requireNonNull(featureServices, "featureServices must not be null");
            return this;
        }

        public Builder enable(DataRegistryFeature... features) {
            Objects.requireNonNull(features, "features must not be null");
            for (DataRegistryFeature feature : features) {
                enabledFeatures.add(Objects.requireNonNull(feature, "feature must not be null"));
            }
            return this;
        }

        /** Enables every built-in DataRegistry feature for broad integration-style feature tests. */
        public Builder enableAll() {
            enabledFeatures.addAll(EnumSet.allOf(DataRegistryFeature.class));
            return this;
        }

        /** Disables selected features without rebuilding the complete enabled-feature set. */
        public Builder disable(DataRegistryFeature... features) {
            Objects.requireNonNull(features, "features must not be null");
            for (DataRegistryFeature feature : features) {
                enabledFeatures.remove(Objects.requireNonNull(feature, "feature must not be null"));
            }
            return this;
        }

        public Builder enabledFeatures(Set<DataRegistryFeature> features) {
            enabledFeatures.clear();
            enabledFeatures.addAll(Objects.requireNonNull(features, "features must not be null"));
            return this;
        }

        public Builder ready(boolean ready) {
            this.ready = ready;
            return this;
        }

        public FakeDataRegistryApi build() {
            Set<DataRegistryFeature> features = Set.copyOf(enabledFeatures);
            PlayerData resolvedPlayers = players == null ? new FakePlayerData(features) : players;
            PopulationData resolvedPopulation = population == null ? new FakePopulationData() : population;
            NetworkSessionApi resolvedSessions = sessions == null ? new FakeNetworkSessionApi() : sessions;
            FeatureServiceDirectory resolvedServices = featureServices == null
                    ? new FakeFeatureServiceDirectory() : featureServices;
            return new FakeDataRegistryApi(
                    resolvedPlayers,
                    resolvedPopulation,
                    resolvedSessions,
                    resolvedServices,
                    features,
                    ready
            );
        }
    }
}
