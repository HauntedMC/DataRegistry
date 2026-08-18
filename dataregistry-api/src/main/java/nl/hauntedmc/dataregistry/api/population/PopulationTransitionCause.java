package nl.hauntedmc.dataregistry.api.population;

/** Explains why a durable population transition was produced. */
public enum PopulationTransitionCause {
    LIVE,
    MIGRATION,
    RECONCILIATION
}
