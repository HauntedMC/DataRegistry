package nl.hauntedmc.dataregistry.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "network_service",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ns_kind_name", columnNames = {"service_kind", "service_name"})
        },
        indexes = {
                @Index(name = "idx_ns_last_seen_at", columnList = "last_seen_at")
        }
)
public class NetworkServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_kind", nullable = false, length = 16, updatable = false)
    private ServiceKind serviceKind;

    @Column(name = "service_name", nullable = false, length = 96, updatable = false)
    private String serviceName;

    @Column(name = "platform", nullable = false, length = 32)
    private String platform;

    @Column(name = "first_seen_at", nullable = false, columnDefinition = "TIMESTAMP", updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant lastSeenAt;

    public NetworkServiceEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ServiceKind getServiceKind() {
        return serviceKind;
    }

    public void setServiceKind(ServiceKind serviceKind) {
        this.serviceKind = serviceKind;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
