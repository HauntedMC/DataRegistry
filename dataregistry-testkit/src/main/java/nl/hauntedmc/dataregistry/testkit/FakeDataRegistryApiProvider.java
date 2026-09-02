package nl.hauntedmc.dataregistry.testkit;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.runtime.RuntimeIdentity;
import nl.hauntedmc.dataregistry.api.runtime.RuntimeKind;

import java.util.Objects;
import java.util.Optional;

/** Configurable {@link DataRegistryApiProvider} fixture for consumer contract tests. */
public final class FakeDataRegistryApiProvider implements DataRegistryApiProvider {

    private final DataRegistryApi dataRegistry;
    private final Optional<RuntimeIdentity> runtimeIdentity;

    public FakeDataRegistryApiProvider(DataRegistryApi dataRegistry, RuntimeIdentity runtimeIdentity) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.runtimeIdentity = Optional.ofNullable(runtimeIdentity);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Creates a provider with a default in-memory API and one proxy runtime identity. */
    public static FakeDataRegistryApiProvider proxy(String serviceName) {
        return builder().proxy(serviceName).build();
    }

    /** Creates a provider with a default in-memory API and one backend runtime identity. */
    public static FakeDataRegistryApiProvider backend(String serviceName) {
        return builder().backend(serviceName).build();
    }

    @Override
    public DataRegistryApi getDataRegistry() {
        return dataRegistry;
    }

    @Override
    public Optional<RuntimeIdentity> getRuntimeIdentity() {
        return runtimeIdentity;
    }

    public static final class Builder {
        private DataRegistryApi dataRegistry;
        private RuntimeIdentity runtimeIdentity;

        private Builder() {
        }

        public Builder dataRegistry(DataRegistryApi dataRegistry) {
            this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
            return this;
        }

        public Builder runtimeIdentity(RuntimeIdentity runtimeIdentity) {
            this.runtimeIdentity = Objects.requireNonNull(runtimeIdentity, "runtimeIdentity must not be null");
            return this;
        }

        public Builder proxy(String serviceName) {
            return runtimeIdentity(new RuntimeIdentity(serviceName, RuntimeKind.PROXY));
        }

        public Builder backend(String serviceName) {
            return runtimeIdentity(new RuntimeIdentity(serviceName, RuntimeKind.BACKEND));
        }

        /** Explicitly models a provider that cannot safely publish physical runtime identity. */
        public Builder withoutRuntimeIdentity() {
            this.runtimeIdentity = null;
            return this;
        }

        public FakeDataRegistryApiProvider build() {
            DataRegistryApi resolvedApi = dataRegistry == null
                    ? FakeDataRegistryApi.builder().build()
                    : dataRegistry;
            return new FakeDataRegistryApiProvider(resolvedApi, runtimeIdentity);
        }
    }
}
