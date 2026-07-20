package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(
        name = "player_session_visits",
        indexes = {
                @Index(name = "idx_psv_player_entered", columnList = "player_id, entered_at"),
                @Index(name = "idx_psv_player_open", columnList = "player_id, left_at"),
                @Index(name = "idx_psv_session_entered", columnList = "session_id, entered_at"),
                @Index(name = "idx_psv_session_open", columnList = "session_id, left_at")
        }
)
public class PlayerSessionVisitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private PlayerEntity player;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private PlayerSessionEntity session;

    @Column(name = "server_name", length = 64, nullable = false)
    private String serverName;

    @Column(name = "entered_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant enteredAt;

    @Column(name = "left_at", columnDefinition = "TIMESTAMP")
    private Instant leftAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PlayerSessionVisitEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
    }

    public PlayerSessionEntity getSession() {
        return session;
    }

    public void setSession(PlayerSessionEntity session) {
        this.session = session;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(Instant enteredAt) {
        this.enteredAt = enteredAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(Instant leftAt) {
        this.leftAt = leftAt;
    }
}
