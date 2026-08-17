package nl.hauntedmc.dataregistry.api.population;

/** Indicates whether an ordinal was allocated live or reconstructed from historical evidence. */
public enum PopulationOrdinalQuality {
    RECORDED_EXACT,
    BACKFILLED_DETERMINISTIC
}
