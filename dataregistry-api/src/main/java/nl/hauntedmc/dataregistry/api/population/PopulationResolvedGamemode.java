package nl.hauntedmc.dataregistry.api.population;

/** Public projection of DataRegistry's canonical backend-server to logical-gamemode resolution. */
public record PopulationResolvedGamemode(
        String serverName,
        String gamemodeKey,
        boolean tracked,
        boolean countedTowardsNetworkTotal
) {
}
