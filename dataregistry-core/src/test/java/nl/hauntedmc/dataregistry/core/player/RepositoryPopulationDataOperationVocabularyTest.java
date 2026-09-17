package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RepositoryPopulationDataOperationVocabularyTest {

    @Test
    void populationFacadeOperationsUseStableLowerCaseVocabulary() {
        for (String operation : List.of(
                RepositoryPopulationData.FIND_SNAPSHOT_OPERATION,
                RepositoryPopulationData.FIND_GAMEMODE_SNAPSHOTS_OPERATION,
                RepositoryPopulationData.FIND_MEMBERSHIP_OPERATION,
                RepositoryPopulationData.FIND_MEMBERSHIPS_OPERATION,
                RepositoryPopulationData.FIND_JOIN_CONTEXT_OPERATION,
                RepositoryPopulationData.FIND_TRANSITIONS_OPERATION,
                RepositoryPopulationData.LATEST_TRANSITION_ID_OPERATION
        )) {
            assertDoesNotThrow(() -> new DataRegistryOperationContext(operation), operation);
        }
    }
}
