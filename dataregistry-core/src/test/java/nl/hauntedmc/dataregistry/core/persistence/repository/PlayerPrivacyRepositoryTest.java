package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.api.player.PlayerDataVisibility;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPrivacyEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerPrivacyRepositoryTest {

    @Test
    void missingPrivacyRowIsPublicAndBatchReadsFillDefaults() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPrivacyEntity> query = mock(Query.class);
        PlayerPrivacyRepository repository = new PlayerPrivacyRepository(ormContext);
        PlayerPrivacyEntity privateSetting = privacy(2L, PlayerDataVisibility.PRIVATE);

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerPrivacyEntity.class, 1L)).thenReturn(null);
        when(session.createQuery(
                "SELECT p FROM PlayerPrivacyEntity p WHERE p.playerId IN :playerIds",
                PlayerPrivacyEntity.class
        )).thenReturn(query);
        when(query.setParameter("playerIds", java.util.Set.of(1L, 2L, 3L))).thenReturn(query);
        when(query.list()).thenReturn(List.of(privateSetting));

        assertEquals(PlayerDataVisibility.PUBLIC, repository.findVisibility(1L));
        assertEquals(
                Map.of(1L, PlayerDataVisibility.PUBLIC, 2L, PlayerDataVisibility.PRIVATE, 3L, PlayerDataVisibility.PUBLIC),
                repository.findVisibilities(List.of(1L, 2L, 3L, 2L))
        );
    }

    @Test
    void savesAllNonPublicValuesAndPublicResetsTheRow() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerPrivacyRepository repository = new PlayerPrivacyRepository(ormContext);
        PlayerEntity player = new PlayerEntity();
        player.setId(4L);
        PlayerPrivacyEntity existing = privacy(4L, PlayerDataVisibility.FRIENDS);

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerPrivacyEntity.class, 4L)).thenReturn(null, existing, existing);
        when(session.getReference(PlayerEntity.class, 4L)).thenReturn(player);
        doAnswer(invocation -> {
            PlayerPrivacyEntity inserted = invocation.getArgument(0);
            assertEquals(PlayerDataVisibility.FRIENDS, inserted.getVisibility());
            return null;
        }).when(session).persist(any(PlayerPrivacyEntity.class));

        repository.saveVisibility(4L, PlayerDataVisibility.FRIENDS);
        repository.saveVisibility(4L, PlayerDataVisibility.PRIVATE);
        repository.saveVisibility(4L, PlayerDataVisibility.PUBLIC);

        verify(session).persist(any(PlayerPrivacyEntity.class));
        assertEquals(PlayerDataVisibility.PRIVATE, existing.getVisibility());
        verify(session).remove(existing);
    }

    @Test
    void rejectsInvalidIdsAndDoesNotOpenTransactions() {
        ORMContext ormContext = mock(ORMContext.class);
        PlayerPrivacyRepository repository = new PlayerPrivacyRepository(ormContext);

        assertThrows(IllegalArgumentException.class, () -> repository.findVisibility(0L));
        assertThrows(IllegalArgumentException.class, () -> repository.findVisibilities(List.of(1L, 0L)));
        assertThrows(IllegalArgumentException.class, () -> repository.saveVisibility(-1L, PlayerDataVisibility.PRIVATE));
        verify(ormContext, never()).runInTransaction(any());
    }

    private static PlayerPrivacyEntity privacy(long playerId, PlayerDataVisibility visibility) {
        PlayerPrivacyEntity entity = new PlayerPrivacyEntity();
        entity.setPlayerId(playerId);
        entity.setVisibility(visibility);
        return entity;
    }
}
