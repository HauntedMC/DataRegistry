package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNicknameEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerNicknameRepositoryTest {

    @Test
    void findNicknameByPlayerIdReturnsEmptyForNull() {
        PlayerNicknameRepository repository = new PlayerNicknameRepository(mock(ORMContext.class));
        assertTrue(repository.findNicknameByPlayerId(null).isEmpty());
    }

    @Test
    void findNicknameByPlayerIdReturnsStoredNickname() {
        PlayerNicknameRepository repository = new PlayerNicknameRepository(mock(ORMContext.class));
        PlayerNicknameRepository spyRepository = org.mockito.Mockito.spy(repository);
        PlayerNicknameEntity entity = new PlayerNicknameEntity();
        entity.setNickname("Ghost");
        org.mockito.Mockito.doReturn(Optional.of(entity)).when(spyRepository).findByPlayerId(4L);

        assertEquals(Optional.of("Ghost"), spyRepository.findNicknameByPlayerId(4L));
    }

    @Test
    void saveOrUpdateCreatesRowWhenMissing() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerNicknameRepository repository = new PlayerNicknameRepository(ormContext);
        PlayerEntity player = new PlayerEntity();
        player.setId(4L);

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerNicknameEntity.class, 4L)).thenReturn(null);
        when(session.getReference(PlayerEntity.class, 4L)).thenReturn(player);

        PlayerNicknameEntity entity = repository.saveOrUpdate(4L, "Ghost");

        verify(session).persist(entity);
        assertEquals(4L, entity.getPlayerId());
        assertEquals(player, entity.getPlayer());
        assertEquals("Ghost", entity.getNickname());
    }

    @Test
    void deleteByPlayerIdRemovesExistingEntity() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerNicknameRepository repository = new PlayerNicknameRepository(ormContext);
        PlayerNicknameEntity existing = new PlayerNicknameEntity();
        existing.setPlayerId(9L);

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerNicknameEntity.class, 9L)).thenReturn(existing);

        repository.deleteByPlayerId(9L);

        verify(session).remove(existing);
    }
}
