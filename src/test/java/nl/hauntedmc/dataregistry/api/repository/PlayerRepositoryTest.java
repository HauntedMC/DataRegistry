package nl.hauntedmc.dataregistry.api.repository;

import jakarta.persistence.PersistenceException;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerRepositoryTest {

    @Test
    void constructorRejectsInvalidUsernameLength() {
        ORMContext ormContext = mock(ORMContext.class);
        assertThrows(IllegalArgumentException.class, () -> new PlayerRepository(ormContext, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerRepository(ormContext, 65));
    }

    @Test
    void findByUuidReturnsEmptyForInvalidUuid() {
        PlayerRepository repository = new PlayerRepository(mock(ORMContext.class));

        assertTrue(repository.findByUUID(null).isEmpty());
        assertTrue(repository.findByUUID("").isEmpty());
        assertTrue(repository.findByUUID("not-a-uuid").isEmpty());
    }

    @Test
    void findByUuidExecutesQueryForValidUuid() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> query = mock(Query.class);
        PlayerRepository repository = new PlayerRepository(ormContext);
        String uuid = UUID.randomUUID().toString();
        PlayerEntity player = new PlayerEntity();
        player.setUuid(uuid);
        player.setUsername("Alice");

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid",
                PlayerEntity.class
        )).thenReturn(query);
        when(query.setParameter("uuid", uuid)).thenReturn(query);
        when(query.uniqueResult()).thenReturn(player);

        Optional<PlayerEntity> result = repository.findByUUID(uuid);

        assertEquals(Optional.of(player), result);
    }

    @Test
    void getOrCreateActivePlayerCreatesNewEntityWhenMissing() {
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = spy(new PlayerRepository(ormContext, 4));
        String uuid = UUID.randomUUID().toString();

        doReturn(Optional.empty()).when(repository).findByUUID(uuid);
        doAnswer(invocation -> {
            PlayerEntity created = invocation.getArgument(0);
            created.setId(10L);
            return created;
        }).when(repository).save(any(PlayerEntity.class));

        PlayerEntity result = repository.getOrCreateActivePlayer(uuid, "  LongUsername  ");

        assertEquals(10L, result.getId());
        assertEquals(uuid, result.getUuid());
        assertEquals("Long", result.getUsername());
        assertEquals(Optional.of(result), repository.getActivePlayer(uuid));
    }

    @Test
    void getOrCreateActivePlayerLoadsExistingEntityAndUpdatesUsernameWhenChanged() {
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = spy(new PlayerRepository(ormContext));
        String uuid = UUID.randomUUID().toString();
        PlayerEntity existing = new PlayerEntity();
        existing.setId(25L);
        existing.setUuid(uuid);
        existing.setUsername("OldName");

        doReturn(Optional.of(existing)).when(repository).findByUUID(uuid);
        doAnswer(invocation -> invocation.getArgument(0)).when(repository).update(any(PlayerEntity.class));

        PlayerEntity result = repository.getOrCreateActivePlayer(uuid, "NewName");

        assertSame(existing, result);
        assertEquals("NewName", result.getUsername());
        verify(repository).update(existing);
    }

    @Test
    void getOrCreateActivePlayerUsesCacheAndAvoidsUpdateWhenUsernameUnchanged() {
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = spy(new PlayerRepository(ormContext));
        String uuid = UUID.randomUUID().toString();
        PlayerEntity existing = new PlayerEntity();
        existing.setId(31L);
        existing.setUuid(uuid);
        existing.setUsername("Alice");

        doReturn(Optional.of(existing)).when(repository).findByUUID(uuid);
        doAnswer(invocation -> invocation.getArgument(0)).when(repository).update(any(PlayerEntity.class));

        PlayerEntity first = repository.getOrCreateActivePlayer(uuid, "Alice");
        clearInvocations(repository);
        PlayerEntity second = repository.getOrCreateActivePlayer(uuid, "Alice");

        assertSame(first, second);
        verify(repository, never()).update(any(PlayerEntity.class));
    }

    @Test
    void getOrCreateActivePlayerRecoversFromDuplicateInsertByReloadingExistingRecord() {
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = spy(new PlayerRepository(ormContext));
        String uuid = UUID.randomUUID().toString();
        PlayerEntity existing = new PlayerEntity();
        existing.setId(40L);
        existing.setUuid(uuid);
        existing.setUsername("Alice");

        doReturn(Optional.empty(), Optional.of(existing)).when(repository).findByUUID(uuid);
        doThrow(new PersistenceException("duplicate key")).when(repository).save(any(PlayerEntity.class));

        PlayerEntity result = repository.getOrCreateActivePlayer(uuid, "Alice");

        assertSame(existing, result);
    }

    @Test
    void getOrCreateActivePlayerThrowsForInvalidInput() {
        PlayerRepository repository = new PlayerRepository(mock(ORMContext.class));
        String validUuid = UUID.randomUUID().toString();

        assertThrows(IllegalArgumentException.class, () -> repository.getOrCreateActivePlayer("bad", "Alice"));
        assertThrows(IllegalArgumentException.class, () -> repository.getOrCreateActivePlayer(validUuid, " "));
    }

    @Test
    void removeActivePlayerDropsCachedEntry() {
        ORMContext ormContext = mock(ORMContext.class);
        PlayerRepository repository = spy(new PlayerRepository(ormContext));
        String uuid = UUID.randomUUID().toString();
        PlayerEntity entity = new PlayerEntity();
        entity.setId(99L);
        entity.setUuid(uuid);
        entity.setUsername("Alice");

        doReturn(Optional.of(entity)).when(repository).findByUUID(uuid);
        repository.getOrCreateActivePlayer(uuid, "Alice");
        assertFalse(repository.getActivePlayer(uuid).isEmpty());

        repository.removeActivePlayer(uuid);

        assertTrue(repository.getActivePlayer(uuid).isEmpty());
    }
}
