package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerOnlineStatusRepositoryTest {

    @Test
    void findByPlayerIdDelegatesToPrimaryKeyLookup() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerOnlineStatusRepository repository = new PlayerOnlineStatusRepository(ormContext);
        PlayerOnlineStatusEntity entity = new PlayerOnlineStatusEntity();

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerOnlineStatusEntity.class, 9L)).thenReturn(entity);

        assertEquals(Optional.of(entity), repository.findByPlayerId(9L));
        assertEquals(Optional.empty(), repository.findByPlayerId(null));
    }

    @Test
    void onlineLookupHelpersApplyMinimumLimits() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerOnlineStatusEntity> onlineQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerOnlineStatusEntity> byServerQuery = mock(Query.class);
        PlayerOnlineStatusRepository repository = new PlayerOnlineStatusRepository(ormContext);
        List<PlayerOnlineStatusEntity> statuses = List.of(new PlayerOnlineStatusEntity());

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerOnlineStatusEntity s WHERE s.online = true ORDER BY s.currentServer ASC, s.playerId ASC",
                PlayerOnlineStatusEntity.class
        )).thenReturn(onlineQuery);
        when(onlineQuery.setMaxResults(1)).thenReturn(onlineQuery);
        when(onlineQuery.list()).thenReturn(statuses);

        when(session.createQuery(
                "SELECT s FROM PlayerOnlineStatusEntity s WHERE s.online = true AND s.currentServer = :currentServer ORDER BY s.playerId ASC",
                PlayerOnlineStatusEntity.class
        )).thenReturn(byServerQuery);
        when(byServerQuery.setParameter("currentServer", "survival")).thenReturn(byServerQuery);
        when(byServerQuery.setMaxResults(1)).thenReturn(byServerQuery);
        when(byServerQuery.list()).thenReturn(statuses);

        assertSame(statuses, repository.findOnlinePlayers(0));
        assertSame(statuses, repository.findOnlinePlayersByServer(" survival ", 0));
        assertEquals(List.of(), repository.findOnlinePlayersByServer(" ", 3));
        verify(onlineQuery).setMaxResults(1);
        verify(byServerQuery).setMaxResults(1);
    }
}
