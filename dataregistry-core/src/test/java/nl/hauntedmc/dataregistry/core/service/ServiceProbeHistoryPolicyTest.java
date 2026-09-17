package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceKind;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceProbeStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceProbeHistoryPolicyTest {

    @Test
    void defaultProbeCadenceArchivesHealthyHistoryEveryFiveMinutes() {
        ServiceProbeHistoryPolicy policy = ServiceProbeHistoryPolicy.fromSettings(DataRegistrySettings.defaults());

        assertEquals(Duration.ofMinutes(5), policy.healthyHistoryInterval());
    }

    @Test
    void healthySteadyStateRefreshesBetweenPeriodicHistorySamples() {
        ServiceProbeHistoryPolicy policy = new ServiceProbeHistoryPolicy(Duration.ofMinutes(5));
        Instant startedAt = Instant.parse("2026-09-17T12:00:00Z");

        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.APPEND_HISTORY,
                policy.decide(ServiceKind.BACKEND, "lobby", "observer-a", ServiceProbeStatus.UP, startedAt)
        );
        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.REFRESH_CURRENT_HEALTHY,
                policy.decide(
                        ServiceKind.BACKEND,
                        "lobby",
                        "observer-a",
                        ServiceProbeStatus.UP,
                        startedAt.plusSeconds(15)
                )
        );
        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.APPEND_HISTORY,
                policy.decide(
                        ServiceKind.BACKEND,
                        "lobby",
                        "observer-a",
                        ServiceProbeStatus.UP,
                        startedAt.plus(Duration.ofMinutes(5))
                )
        );
    }

    @Test
    void failuresAndRecoveriesAreAlwaysArchived() {
        ServiceProbeHistoryPolicy policy = new ServiceProbeHistoryPolicy(Duration.ofMinutes(5));
        Instant startedAt = Instant.parse("2026-09-17T12:00:00Z");

        policy.decide(ServiceKind.BACKEND, "lobby", "observer-a", ServiceProbeStatus.UP, startedAt);
        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.APPEND_HISTORY,
                policy.decide(
                        ServiceKind.BACKEND,
                        "lobby",
                        "observer-a",
                        ServiceProbeStatus.TIMEOUT,
                        startedAt.plusSeconds(30)
                )
        );
        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.APPEND_HISTORY,
                policy.decide(
                        ServiceKind.BACKEND,
                        "lobby",
                        "observer-a",
                        ServiceProbeStatus.DOWN,
                        startedAt.plusSeconds(45)
                )
        );
        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.APPEND_HISTORY,
                policy.decide(
                        ServiceKind.BACKEND,
                        "lobby",
                        "observer-a",
                        ServiceProbeStatus.UP,
                        startedAt.plusSeconds(60)
                )
        );
    }

    @Test
    void observersHaveIndependentHealthyHistoryCadence() {
        ServiceProbeHistoryPolicy policy = new ServiceProbeHistoryPolicy(Duration.ofMinutes(5));
        Instant now = Instant.parse("2026-09-17T12:00:00Z");

        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.APPEND_HISTORY,
                policy.decide(ServiceKind.BACKEND, "lobby", "observer-a", ServiceProbeStatus.UP, now)
        );
        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.APPEND_HISTORY,
                policy.decide(ServiceKind.BACKEND, "lobby", "observer-b", ServiceProbeStatus.UP, now.plusSeconds(15))
        );
        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.REFRESH_CURRENT_HEALTHY,
                policy.decide(ServiceKind.BACKEND, "lobby", "observer-a", ServiceProbeStatus.UP, now.plusSeconds(30))
        );
    }

    @Test
    void failedScheduledHealthyArchiveIsRetriedOnNextSuccess() {
        ServiceProbeHistoryPolicy policy = new ServiceProbeHistoryPolicy(Duration.ofMinutes(5));
        Instant now = Instant.parse("2026-09-17T12:00:00Z");
        ServiceProbeHistoryPolicy.WriteMode mode = policy.decide(
                ServiceKind.BACKEND,
                "lobby",
                "observer-a",
                ServiceProbeStatus.UP,
                now
        );

        policy.onWriteFailure(
                ServiceKind.BACKEND,
                "lobby",
                "observer-a",
                ServiceProbeStatus.UP,
                mode
        );

        assertEquals(
                ServiceProbeHistoryPolicy.WriteMode.APPEND_HISTORY,
                policy.decide(
                        ServiceKind.BACKEND,
                        "lobby",
                        "observer-a",
                        ServiceProbeStatus.UP,
                        now.plusSeconds(15)
                )
        );
    }
}
