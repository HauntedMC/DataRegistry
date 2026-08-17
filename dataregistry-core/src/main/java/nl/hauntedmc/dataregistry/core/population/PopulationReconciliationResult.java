package nl.hauntedmc.dataregistry.core.population;

/** Summary of an online-count reconciliation pass. */
public record PopulationReconciliationResult(int scopesChanged, int peaksRaised) {
    public boolean changedAnything() {
        return scopesChanged > 0 || peaksRaised > 0;
    }
}
