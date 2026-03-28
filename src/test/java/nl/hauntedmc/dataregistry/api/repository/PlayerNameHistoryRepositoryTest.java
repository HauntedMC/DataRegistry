package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerNameHistoryRepositoryTest {

    @Test
    void findHelpersQueryLatestAndRecentNameHistory() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerNameHistoryEntity> latestQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerNameHistoryEntity> byPlayerAndNameQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerNameHistoryEntity> recentByPlayerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerNameHistoryEntity> recentByUsernameQuery = mock(Query.class);
        PlayerNameHistoryRepository repository = new PlayerNameHistoryRepository(ormContext);
        PlayerNameHistoryEntity entry = new PlayerNameHistoryEntity();

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT h FROM PlayerNameHistoryEntity h WHERE h.player.id = :playerId ORDER BY h.lastSeenAt DESC",
                PlayerNameHistoryEntity.class
        )).thenReturn(latestQuery, recentByPlayerQuery);
        when(latestQuery.setParameter("playerId", 22L)).thenReturn(latestQuery);
        when(latestQuery.setMaxResults(1)).thenReturn(latestQuery);
        when(latestQuery.uniqueResultOptional()).thenReturn(Optional.of(entry));

        when(session.createQuery(
                "SELECT h FROM PlayerNameHistoryEntity h WHERE h.player.id = :playerId AND h.username = :username",
                PlayerNameHistoryEntity.class
        )).thenReturn(byPlayerAndNameQuery);
        when(byPlayerAndNameQuery.setParameter("playerId", 22L)).thenReturn(byPlayerAndNameQuery);
        when(byPlayerAndNameQuery.setParameter("username", "Alice")).thenReturn(byPlayerAndNameQuery);
        when(byPlayerAndNameQuery.setMaxResults(1)).thenReturn(byPlayerAndNameQuery);
        when(byPlayerAndNameQuery.uniqueResultOptional()).thenReturn(Optional.of(entry));

        when(recentByPlayerQuery.setParameter("playerId", 22L)).thenReturn(recentByPlayerQuery);
        when(recentByPlayerQuery.setMaxResults(1)).thenReturn(recentByPlayerQuery);
        when(recentByPlayerQuery.list()).thenReturn(List.of(entry));

        when(session.createQuery(
                "SELECT h FROM PlayerNameHistoryEntity h WHERE h.username = :username ORDER BY h.lastSeenAt DESC",
                PlayerNameHistoryEntity.class
        )).thenReturn(recentByUsernameQuery);
        when(recentByUsernameQuery.setParameter("username", "Alice")).thenReturn(recentByUsernameQuery);
        when(recentByUsernameQuery.setMaxResults(1)).thenReturn(recentByUsernameQuery);
        when(recentByUsernameQuery.list()).thenReturn(List.of(entry));

        assertEquals(Optional.of(entry), repository.findLatestForPlayer(22L));
        assertEquals(Optional.of(entry), repository.findByPlayerAndUsername(22L, "Alice"));
        assertEquals(List.of(entry), repository.findRecentByPlayer(22L, 0));
        assertEquals(List.of(entry), repository.findRecentByUsername("Alice", 0));
        verify(recentByPlayerQuery).setMaxResults(1);
        verify(recentByUsernameQuery).setMaxResults(1);
    }

    @Test
    void findHelpersRejectNullRequiredArguments() {
        PlayerNameHistoryRepository repository = new PlayerNameHistoryRepository(mock(ORMContext.class));

        assertThrows(NullPointerException.class, () -> repository.findLatestForPlayer(null));
        assertThrows(NullPointerException.class, () -> repository.findByPlayerAndUsername(null, "Alice"));
        assertThrows(NullPointerException.class, () -> repository.findByPlayerAndUsername(1L, null));
        assertThrows(NullPointerException.class, () -> repository.findRecentByPlayer(null, 10));
        assertThrows(NullPointerException.class, () -> repository.findRecentByUsername(null, 10));
    }
}
