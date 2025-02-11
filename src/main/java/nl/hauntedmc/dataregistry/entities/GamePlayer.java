package nl.hauntedmc.dataregistry.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "players")
public class GamePlayer {

    @Id
    @Column(name = "uuid", length = 36, nullable = false)
    private String uuid;

    @Column(name = "username", length = 32, nullable = false)
    private String username;

    // isOnline: 1 for online, 0 for offline.
    @Column(name = "is_online", nullable = false)
    private int isOnline;

    public GamePlayer() { }

    // Getters and setters
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
    public int getIsOnline() {
        return isOnline;
    }
    public void setIsOnline(int isOnline) {
        this.isOnline = isOnline;
    }
}
