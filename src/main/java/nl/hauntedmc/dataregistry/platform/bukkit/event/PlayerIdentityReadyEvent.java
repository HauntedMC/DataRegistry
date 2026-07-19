package nl.hauntedmc.dataregistry.platform.bukkit.event;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired on the Bukkit main thread after DataRegistry has prepared a player's identity.
 */
public class PlayerIdentityReadyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlayerIdentity identity;

    /**
     * Creates an event for a player identity that has completed DataRegistry join preparation.
     *
     * @param identity immutable player identity snapshot.
     */
    public PlayerIdentityReadyEvent(PlayerIdentity identity) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
    }

    /**
     * Returns the immutable DataRegistry identity that is now safe for feature lookups.
     */
    public PlayerIdentity identity() {
        return identity;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the Bukkit handler list for this event type.
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
