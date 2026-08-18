package nl.hauntedmc.dataregistry.api.population;

/** Durable population state changes available to downstream consumers. */
public enum PopulationTransitionType {
    MEMBERSHIP_ADDED,
    ONLINE_CHANGED,
    ONLINE_PEAK_CHANGED
}
