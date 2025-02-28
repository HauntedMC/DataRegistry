package nl.hauntedmc.dataregistry.entities;

import jakarta.persistence.*;

@Entity(name = "PlayerEntity")
@Table(name = "player_entity")
public class PlayerEntity {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", length = 36, unique = true, nullable = false)
    private String uuid;

    @Column(name = "username", length = 32, nullable = false)
    private String username;

    public PlayerEntity() { }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
