package nl.hauntedmc.dataregistry.api.player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cursor page for player read APIs.
 */
public record PlayerPage<T>(List<T> items, Optional<String> nextCursor) {

    public PlayerPage {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        nextCursor = Objects.requireNonNull(nextCursor, "nextCursor must not be null");
    }

    public boolean hasNext() {
        return nextCursor.isPresent();
    }
}
