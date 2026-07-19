package nl.hauntedmc.dataregistry.api.service;

import java.util.Objects;

/**
 * Immutable metadata describing a feature-owned service exported through DataRegistry.
 */
public record FeatureServiceInfo(
        String ownerPlugin,
        String ownerFeature,
        Class<?> apiType,
        String implementationType
) {

    public FeatureServiceInfo {
        ownerPlugin = requireText(ownerPlugin, "ownerPlugin");
        ownerFeature = requireText(ownerFeature, "ownerFeature");
        Objects.requireNonNull(apiType, "apiType must not be null");
        implementationType = requireText(implementationType, "implementationType");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
