package nl.hauntedmc.dataregistry.api.player;

/** Result of an idempotent, version-checked preference write. */
public record PlayerLanguageMutationResult(Status status, long version) {
    public enum Status { APPLIED, REPLAYED, CONFLICT, STALE_IDENTITY }
}
