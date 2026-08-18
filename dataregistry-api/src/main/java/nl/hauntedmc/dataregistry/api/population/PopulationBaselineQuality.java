package nl.hauntedmc.dataregistry.api.population;

/** Describes how confidently a population scope covers history that predates population tracking. */
public enum PopulationBaselineQuality {
    /** All historical membership/peak state is known or has been explicitly verified by an administrator. */
    VERIFIED,
    /** The value is exact for tracked data, but older history may predate DataRegistry coverage. */
    TRACKED_ONLY
}
