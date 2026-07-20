package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;

import java.util.Set;

/**
 * Public, persistence-agnostic DataRegistry contract.
 * <p>
 * This is the only DataRegistry type that platform plugins publish to other features. It deliberately exposes
 * immutable read models and domain facades only; ORM contexts, entities, repositories, and lifecycle writers are
 * implementation details of {@code dataregistry-core}.
 */
public interface DataRegistryApi {

    /**
     * Returns the player-facing domain facade.
     */
    PlayerData players();

    /**
     * Returns the catalog of feature-owned public service interfaces.
     */
    FeatureServiceDirectory featureServices();

    /**
     * Returns the enabled built-in capabilities for this runtime.
     */
    Set<DataRegistryFeature> enabledFeatures();

    /**
     * Returns whether a built-in capability is enabled.
     */
    boolean supports(DataRegistryFeature feature);

    /**
     * Returns whether the public facade is ready to serve requests.
     */
    boolean isReady();
}
