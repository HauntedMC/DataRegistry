package nl.hauntedmc.dataregistry.api.session;

/** Public session foundation shared by proxy and backend features. */
public interface NetworkSessionApi extends SessionDirectory {
    SessionDirectoryHealth health();
}
