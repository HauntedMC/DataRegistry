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

import java.time.Instant;

@Entity
@Table(
        name = "service_probe",
        indexes = {
                @Index(name = "idx_sp_service_checked", columnList = "service_id, checked_at"),
                @Index(name = "idx_sp_observer_checked", columnList = "observer_instance_id, checked_at"),
                @Index(name = "idx_sp_status_checked", columnList = "status, checked_at"),
                @Index(name = "idx_sp_checked_at", columnList = "checked_at")
        }
)
public class ServiceProbeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private NetworkServiceEntity service;

    @Column(name = "target_instance_id", length = 36)
    private String targetInstanceId;

    @Column(name = "observer_instance_id", nullable = false, length = 36)
    private String observerInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ServiceProbeStatus status;

    @Column(name = "target_host", length = 255)
    private String targetHost;

    @Column(name = "target_port")
    private Integer targetPort;

    @Column(name = "latency_millis")
    private Long latencyMillis;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_detail", length = 255)
    private String errorDetail;

    @Column(name = "checked_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant checkedAt;

    public ServiceProbeEntity() {
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

    public String getTargetInstanceId() {
        return targetInstanceId;
    }

    public void setTargetInstanceId(String targetInstanceId) {
        this.targetInstanceId = targetInstanceId;
    }

    public String getObserverInstanceId() {
        return observerInstanceId;
    }

    public void setObserverInstanceId(String observerInstanceId) {
        this.observerInstanceId = observerInstanceId;
    }

    public ServiceProbeStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceProbeStatus status) {
        this.status = status;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public Integer getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    public Long getLatencyMillis() {
        return latencyMillis;
    }

    public void setLatencyMillis(Long latencyMillis) {
        this.latencyMillis = latencyMillis;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }
}
