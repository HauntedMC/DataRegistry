package nl.hauntedmc.dataregistry.api.runtime;

import java.util.Objects;

/** Immutable identity of the physical runtime hosting the active DataRegistry provider. */
public record RuntimeIdentity(String serviceName, RuntimeKind kind) {

    /** Maximum service-name length accepted by DataRegistry configuration and persistence. */
    public static final int MAX_SERVICE_NAME_LENGTH = 96;

    public RuntimeIdentity {
        if (serviceName == null) {
            throw new IllegalArgumentException("serviceName must not be null");
        }
        String normalizedServiceName = serviceName.trim();
        if (normalizedServiceName.isEmpty()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        if (normalizedServiceName.length() > MAX_SERVICE_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "serviceName must not exceed " + MAX_SERVICE_NAME_LENGTH + " characters"
            );
        }
        serviceName = normalizedServiceName;
        kind = Objects.requireNonNull(kind, "kind must not be null");
    }
}
