package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerConnectionInfoRepositoryTest {

    @Test
    void findByPlayerIdDelegatesToPrimaryKeyLookup() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerConnectionInfoRepository repository = new PlayerConnectionInfoRepository(ormContext);
        PlayerConnectionInfoEntity entity = new PlayerConnectionInfoEntity();

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerConnectionInfoEntity.class, 4L)).thenReturn(entity);

        assertEquals(Optional.of(entity), repository.findByPlayerId(4L));
        assertEquals(Optional.empty(), repository.findByPlayerId(null));
    }

    @Test
    void lookupHelpersReturnUsernamesAndIdsForSharedIp() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<String> usernameQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> idQuery = mock(Query.class);
        PlayerConnectionInfoRepository repository = new PlayerConnectionInfoRepository(ormContext);

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT c.player.username FROM PlayerConnectionInfoEntity c WHERE c.ipAddress = :ip " +
                        "AND (:excludePlayerId IS NULL OR c.player.id <> :excludePlayerId) ORDER BY c.player.username ASC",
                String.class
        )).thenReturn(usernameQuery);
        when(usernameQuery.setParameter("ip", "1.2.3.4")).thenReturn(usernameQuery);
        when(usernameQuery.setParameter("excludePlayerId", 8L)).thenReturn(usernameQuery);
        when(usernameQuery.list()).thenReturn(List.of("Alice", "Bob"));

        when(session.createQuery(
                "SELECT c.player.id FROM PlayerConnectionInfoEntity c WHERE c.ipAddress = :ip " +
                        "AND (:excludePlayerId IS NULL OR c.player.id <> :excludePlayerId) ORDER BY c.player.id ASC",
                Long.class
        )).thenReturn(idQuery);
        when(idQuery.setParameter("ip", "1.2.3.4")).thenReturn(idQuery);
        when(idQuery.setParameter("excludePlayerId", 8L)).thenReturn(idQuery);
        when(idQuery.list()).thenReturn(List.of(3L, 5L));

        List<String> usernames = repository.findUsernamesByLastIpAddress(" 1.2.3.4 ", 8L);
        List<Long> playerIds = repository.findPlayerIdsByLastIpAddress("1.2.3.4", 8L);

        assertEquals(List.of("Alice", "Bob"), usernames);
        assertEquals(List.of(3L, 5L), playerIds);
        verify(usernameQuery).setParameter("excludePlayerId", 8L);
        verify(idQuery).setParameter("excludePlayerId", 8L);
    }

    @Test
    void identityLookupReturnsImmutablePlayerSnapshotsForSharedIp() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<Object[]> query = mock(Query.class);
        PlayerConnectionInfoRepository repository = new PlayerConnectionInfoRepository(ormContext);
        UUID uuid = UUID.randomUUID();

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT c.player.id, c.player.uuid, c.player.username FROM PlayerConnectionInfoEntity c " +
                        "WHERE c.ipAddress = :ip " +
                        "AND (:excludePlayerId IS NULL OR c.player.id <> :excludePlayerId) " +
                        "ORDER BY c.player.username ASC",
                Object[].class
        )).thenReturn(query);
        when(query.setParameter("ip", "1.2.3.4")).thenReturn(query);
        when(query.setParameter("excludePlayerId", null)).thenReturn(query);
        when(query.list()).thenReturn(List.<Object[]>of(new Object[]{4L, uuid.toString(), "Alice"}));

        List<PlayerIdentity> identities = repository.findIdentitiesByLastIpAddress(" 1.2.3.4 ", null);

        assertEquals(List.of(new PlayerIdentity(4L, uuid, "Alice")), identities);
    }
}
