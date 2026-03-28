package nl.hauntedmc.dataregistry.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "service_instance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_si_instance_id", columnNames = {"instance_id"})
        },
        indexes = {
                @Index(name = "idx_si_service_last_seen", columnList = "service_id, last_seen_at")
        }
)
public class ServiceInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private NetworkServiceEntity service;

    @Column(name = "instance_id", nullable = false, updatable = false, length = 36)
    private String instanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ServiceInstanceStatus status = ServiceInstanceStatus.RUNNING;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "version", length = 96)
    private String version;

    @Column(name = "started_at", nullable = false, columnDefinition = "TIMESTAMP", updatable = false)
    private Instant startedAt;

    @Column(name = "last_seen_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant lastSeenAt;

    @Column(name = "stopped_at", columnDefinition = "TIMESTAMP")
    private Instant stoppedAt;

    public ServiceInstanceEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NetworkServiceEntity getService() {
        return service;
    }

    public void setService(NetworkServiceEntity service) {
        this.service = service;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public ServiceInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceInstanceStatus status) {
        this.status = status;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
