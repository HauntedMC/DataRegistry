package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "tracked_gamemodes")
public class TrackedGamemodeEntity {

    @Id
    @Column(name = "gamemode_key", length = 64, nullable = false, updatable = false)
    private String gamemodeKey;

    @Column(name = "counted_towards_network_total", nullable = false)
    private boolean countedTowardsNetworkTotal;

    @Column(name = "first_observed_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant firstObservedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public String getGamemodeKey() {
        return gamemodeKey;
    }

    public void setGamemodeKey(String gamemodeKey) {
        this.gamemodeKey = gamemodeKey;
    }

    public boolean isCountedTowardsNetworkTotal() {
        return countedTowardsNetworkTotal;
    }

    public void setCountedTowardsNetworkTotal(boolean countedTowardsNetworkTotal) {
        this.countedTowardsNetworkTotal = countedTowardsNetworkTotal;
    }

    public Instant getFirstObservedAt() {
        return firstObservedAt;
    }

    public void setFirstObservedAt(Instant firstObservedAt) {
        this.firstObservedAt = firstObservedAt;
    }
}
