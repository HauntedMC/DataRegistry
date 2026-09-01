package nl.hauntedmc.dataregistry.core.config;

/** Policy used when Redis cannot confirm renewal of a locally owned player session. */
public enum SessionRedisOutageBehavior {
    /** Keep the connection only until the last Redis-authoritative lease expiry (minus its safety margin). */
    PRESERVE_UNTIL_EXPIRY,
    /** Disconnect as soon as a renewal attempt fails, even while the last confirmed lease is still live. */
    DISCONNECT_IMMEDIATELY
}
