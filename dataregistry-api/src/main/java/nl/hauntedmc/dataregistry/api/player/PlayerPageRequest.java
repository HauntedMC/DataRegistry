package nl.hauntedmc.dataregistry.api.player;

/**
 * Cursor request for player lookup pages.
 *
 * @param afterCursor opaque cursor returned by the previous page, or {@code null} for the first page.
 * @param limit       maximum number of rows to return.
 */
public record PlayerPageRequest(String afterCursor, int limit) {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 500;

    public PlayerPageRequest {
        afterCursor = afterCursor == null || afterCursor.isBlank() ? null : afterCursor.trim();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ".");
        }
    }

    public static PlayerPageRequest firstPage(int limit) {
        return new PlayerPageRequest(null, limit);
    }
}
