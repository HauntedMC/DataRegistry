package nl.hauntedmc.dataregistry.core.player;

/** The website link no longer resolves to the same canonical player row. */
public final class StalePlayerIdentityException extends RuntimeException {
    public StalePlayerIdentityException() { super("Player identity no longer matches the linked account"); }
}
