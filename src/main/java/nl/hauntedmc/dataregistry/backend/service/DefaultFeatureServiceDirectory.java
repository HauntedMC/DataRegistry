package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of the feature service catalog.
 */
public final class DefaultFeatureServiceDirectory implements FeatureServiceDirectory {

    private final ConcurrentHashMap<Class<?>, Registration<?>> services = new ConcurrentHashMap<>();

    @Override
    public <T> FeatureServiceHandle register(String ownerPlugin, String ownerFeature, Class<T> apiType, T service) {
        Objects.requireNonNull(apiType, "apiType must not be null");
        Objects.requireNonNull(service, "service must not be null");
        if (!apiType.isInstance(service)) {
            throw new IllegalArgumentException("Service " + service.getClass().getName()
                    + " does not implement " + apiType.getName() + ".");
        }

        FeatureServiceInfo info = new FeatureServiceInfo(
                ownerPlugin,
                ownerFeature,
                apiType,
                service.getClass().getName()
        );
        Registration<T> registration = new Registration<>(info, service);
        services.put(apiType, registration);
        return new Handle(apiType, service, info);
    }

    @Override
    public <T> Optional<T> find(Class<T> apiType) {
        Objects.requireNonNull(apiType, "apiType must not be null");
        Registration<?> registration = services.get(apiType);
        return registration == null ? Optional.empty() : Optional.of(apiType.cast(registration.service()));
    }

    @Override
    public <T> T require(Class<T> apiType) {
        return find(apiType).orElseThrow(() ->
                new IllegalStateException("Feature service is not registered: " + apiType.getName() + ".")
        );
    }

    @Override
    public boolean contains(Class<?> apiType) {
        Objects.requireNonNull(apiType, "apiType must not be null");
        return services.containsKey(apiType);
    }

    @Override
    public Optional<FeatureServiceInfo> describe(Class<?> apiType) {
        Objects.requireNonNull(apiType, "apiType must not be null");
        Registration<?> registration = services.get(apiType);
        return registration == null ? Optional.empty() : Optional.of(registration.info());
    }

    @Override
    public List<FeatureServiceInfo> list() {
        return services.values().stream()
                .map(Registration::info)
                .sorted(Comparator.comparing((FeatureServiceInfo info) -> info.ownerPlugin().toLowerCase(Locale.ROOT))
                        .thenComparing(info -> info.ownerFeature().toLowerCase(Locale.ROOT))
                        .thenComparing(info -> info.apiType().getName()))
                .toList();
    }

    @Override
    public boolean unregister(Class<?> apiType, Object service) {
        Objects.requireNonNull(apiType, "apiType must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Registration<?> registration = services.get(apiType);
        return registration != null && registration.service() == service && services.remove(apiType, registration);
    }

    @Override
    public int unregisterOwner(String ownerPlugin, String ownerFeature) {
        String normalizedPlugin = requireText(ownerPlugin, "ownerPlugin");
        String normalizedFeature = requireText(ownerFeature, "ownerFeature");
        int removed = 0;
        for (var entry : services.entrySet()) {
            FeatureServiceInfo info = entry.getValue().info();
            if (info.ownerPlugin().equals(normalizedPlugin) && info.ownerFeature().equals(normalizedFeature)
                    && services.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    @Override
    public void clear() {
        services.clear();
    }

    private record Registration<T>(FeatureServiceInfo info, T service) {
        private Registration {
            Objects.requireNonNull(info, "info must not be null");
            Objects.requireNonNull(service, "service must not be null");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private final class Handle implements FeatureServiceHandle {

        private final Class<?> apiType;
        private final Object service;
        private final FeatureServiceInfo info;

        private Handle(Class<?> apiType, Object service, FeatureServiceInfo info) {
            this.apiType = apiType;
            this.service = service;
            this.info = info;
        }

        @Override
        public FeatureServiceInfo info() {
            return info;
        }

        @Override
        public void close() {
            unregister(apiType, service);
        }
    }
}
