package nl.hauntedmc.dataregistry.core.population;

/** Summary of a population presence reconciliation pass. */
public record PopulationReconciliationResult(int reconciledScopes, int peakChanges) {
    public boolean changedAnything() {
        return reconciledScopes > 0 || peakChanges > 0;
    }
}
