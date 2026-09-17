package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceKind;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceProbeStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decides whether a probe observation should become a durable history row or refresh the current healthy sample.
 *
 * <p>Failures are always durable. Healthy steady state is archived periodically while intermediate successes refresh
 * the observer's newest healthy row in place. Policy state is scoped to one service + observer so multiple proxies can
 * compact independently without overwriting each other's current observations.</p>
 */
final class ServiceProbeHistoryPolicy {

    private static final Duration MIN_HEALTHY_HISTORY_INTERVAL = Duration.ofMinutes(5);
    private static final Duration MAX_HEALTHY_HISTORY_INTERVAL = Duration.ofMinutes(30);
    private static final int HEALTHY_HISTORY_PROBE_MULTIPLIER = 20;

    private final Duration healthyHistoryInterval;
    private final ConcurrentMap<ProbeKey, Instant> nextHealthyArchiveAt = new ConcurrentHashMap<>();

    ServiceProbeHistoryPolicy(Duration healthyHistoryInterval) {
        this.healthyHistoryInterval = Objects.requireNonNull(
                healthyHistoryInterval,
                "healthyHistoryInterval must not be null"
        );
        if (healthyHistoryInterval.isZero() || healthyHistoryInterval.isNegative()) {
            throw new IllegalArgumentException("healthyHistoryInterval must be positive");
        }
    }

    static ServiceProbeHistoryPolicy fromSettings(DataRegistrySettings settings) {
        DataRegistrySettings effectiveSettings = settings == null ? DataRegistrySettings.defaults() : settings;
        long desiredSeconds = Math.multiplyExact(
                (long) effectiveSettings.serviceProbeIntervalSeconds(),
                HEALTHY_HISTORY_PROBE_MULTIPLIER
        );
        Duration desired = Duration.ofSeconds(desiredSeconds);
        Duration bounded = desired.compareTo(MIN_HEALTHY_HISTORY_INTERVAL) < 0
                ? MIN_HEALTHY_HISTORY_INTERVAL
                : desired.compareTo(MAX_HEALTHY_HISTORY_INTERVAL) > 0
                        ? MAX_HEALTHY_HISTORY_INTERVAL
                        : desired;
        return new ServiceProbeHistoryPolicy(bounded);
    }

    WriteMode decide(
            ServiceKind serviceKind,
            String serviceName,
            String observerInstanceId,
            ServiceProbeStatus status,
            Instant now
    ) {
        Objects.requireNonNull(serviceKind, "serviceKind must not be null");
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(observerInstanceId, "observerInstanceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(now, "now must not be null");

        ProbeKey key = new ProbeKey(serviceKind, serviceName, observerInstanceId);
        if (status != ServiceProbeStatus.UP) {
            nextHealthyArchiveAt.remove(key);
            return WriteMode.APPEND_HISTORY;
        }

        AtomicBoolean appendHistory = new AtomicBoolean(false);
        nextHealthyArchiveAt.compute(key, (ignored, nextArchiveAt) -> {
            if (nextArchiveAt == null || !now.isBefore(nextArchiveAt)) {
                appendHistory.set(true);
                return now.plus(healthyHistoryInterval);
            }
            return nextArchiveAt;
        });
        return appendHistory.get() ? WriteMode.APPEND_HISTORY : WriteMode.REFRESH_CURRENT_HEALTHY;
    }

    /**
     * Re-opens an archival slot when a scheduled healthy-history insert failed so the next successful probe retries it.
     */
    void onWriteFailure(
            ServiceKind serviceKind,
            String serviceName,
            String observerInstanceId,
            ServiceProbeStatus status,
            WriteMode writeMode
    ) {
        if (status == ServiceProbeStatus.UP && writeMode == WriteMode.APPEND_HISTORY) {
            nextHealthyArchiveAt.remove(new ProbeKey(serviceKind, serviceName, observerInstanceId));
        }
    }

    Duration healthyHistoryInterval() {
        return healthyHistoryInterval;
    }

    enum WriteMode {
        APPEND_HISTORY,
        REFRESH_CURRENT_HEALTHY
    }

    private record ProbeKey(ServiceKind serviceKind, String serviceName, String observerInstanceId) {
    }
}
