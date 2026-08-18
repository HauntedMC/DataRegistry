package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mutable in-memory feature-service catalog for downstream contract tests. */
public final class FakeFeatureServiceDirectory implements FeatureServiceDirectory {

    private final Map<Class<?>, Registration> registrations = new ConcurrentHashMap<>();

    @Override
    public <T> FeatureServiceHandle register(String ownerPlugin, String ownerFeature, Class<T> apiType, T service) {
        Objects.requireNonNull(apiType, "apiType must not be null");
        Objects.requireNonNull(service, "service must not be null");
        if (!apiType.isInstance(service)) {
            throw new IllegalArgumentException("service must implement " + apiType.getName());
        }

        FeatureServiceInfo info = new FeatureServiceInfo(
                ownerPlugin,
                ownerFeature,
                apiType,
                service.getClass().getName()
        );
        Registration replacement = new Registration(info, service);
        registrations.compute(apiType, (ignored, existing) -> {
            if (existing != null && !existing.sameOwner(info)) {
                throw new IllegalStateException(
                        "Feature service " + apiType.getName() + " is already registered by "
                                + existing.info.ownerPlugin() + "/" + existing.info.ownerFeature()
                );
            }
            return replacement;
        });
        return new Handle(apiType, replacement);
    }

    @Override
    public <T> Optional<T> find(Class<T> apiType) {
        Objects.requireNonNull(apiType, "apiType must not be null");
        Registration registration = registrations.get(apiType);
        return registration == null ? Optional.empty() : Optional.of(apiType.cast(registration.service));
    }

    @Override
    public <T> T require(Class<T> apiType) {
        return find(apiType).orElseThrow(() -> new IllegalStateException(
                "Required feature service is not registered: " + apiType.getName()
        ));
    }

    @Override
    public boolean contains(Class<?> apiType) {
        return apiType != null && registrations.containsKey(apiType);
    }

    @Override
    public Optional<FeatureServiceInfo> describe(Class<?> apiType) {
        if (apiType == null) {
            return Optional.empty();
        }
        Registration registration = registrations.get(apiType);
        return registration == null ? Optional.empty() : Optional.of(registration.info);
    }

    @Override
    public List<FeatureServiceInfo> list() {
        return registrations.values().stream()
                .map(registration -> registration.info)
                .sorted(Comparator.comparing(info -> info.apiType().getName()))
                .toList();
    }

    @Override
    public boolean unregister(Class<?> apiType, Object service) {
        if (apiType == null || service == null) {
            return false;
        }
        AtomicBoolean removed = new AtomicBoolean();
        registrations.computeIfPresent(apiType, (ignored, existing) -> {
            if (existing.service != service) {
                return existing;
            }
            removed.set(true);
            return null;
        });
        return removed.get();
    }

    @Override
    public int unregisterOwner(String ownerPlugin, String ownerFeature) {
        String normalizedPlugin = normalizeOwner(ownerPlugin, "ownerPlugin");
        String normalizedFeature = normalizeOwner(ownerFeature, "ownerFeature");
        AtomicBoolean removed = new AtomicBoolean();
        int count = 0;
        for (Map.Entry<Class<?>, Registration> entry : registrations.entrySet()) {
            Registration registration = entry.getValue();
            if (registration.info.ownerPlugin().equals(normalizedPlugin)
                    && registration.info.ownerFeature().equals(normalizedFeature)) {
                removed.set(false);
                registrations.computeIfPresent(entry.getKey(), (ignored, current) -> {
                    if (current == registration) {
                        removed.set(true);
                        return null;
                    }
                    return current;
                });
                if (removed.get()) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public void clear() {
        registrations.clear();
    }

    private static String normalizeOwner(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private record Registration(FeatureServiceInfo info, Object service) {
        private boolean sameOwner(FeatureServiceInfo other) {
            return info.ownerPlugin().equals(other.ownerPlugin())
                    && info.ownerFeature().equals(other.ownerFeature());
        }
    }

    private final class Handle implements FeatureServiceHandle {
        private final Class<?> apiType;
        private final Registration registration;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Handle(Class<?> apiType, Registration registration) {
            this.apiType = apiType;
            this.registration = registration;
        }

        @Override
        public FeatureServiceInfo info() {
            return registration.info;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            registrations.computeIfPresent(apiType, (ignored, current) ->
                    current == registration ? null : current
            );
        }
    }
}
