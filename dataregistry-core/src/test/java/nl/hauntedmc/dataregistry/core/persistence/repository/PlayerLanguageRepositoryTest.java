package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerLanguageEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerLanguageRepositoryTest {

    @Test
    void findByPlayerIdReturnsEmptyForNull() {
        PlayerLanguageRepository repository = new PlayerLanguageRepository(mock(ORMContext.class));
        assertTrue(repository.findByPlayerId(null).isEmpty());
    }

    @Test
    void saveOrUpdateCreatesRowWhenMissing() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerLanguageRepository repository = new PlayerLanguageRepository(ormContext);
        PlayerEntity player = new PlayerEntity();
        player.setId(4L);

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerLanguageEntity.class, 4L)).thenReturn(null);
        when(session.getReference(PlayerEntity.class, 4L)).thenReturn(player);
        doAnswer(invocation -> {
            PlayerLanguageEntity persisted = invocation.getArgument(0);
            assertEquals("AUTO", persisted.getLanguage());
            assertEquals("EN", persisted.getEffectiveLanguage());
            return null;
        }).when(session).persist(any(PlayerLanguageEntity.class));

        PlayerLanguageEntity entity = repository.saveOrUpdate(4L, "AUTO", "EN");

        verify(session).persist(entity);
        assertEquals(4L, entity.getPlayerId());
        assertEquals(player, entity.getPlayer());
        assertEquals("AUTO", entity.getLanguage());
        assertEquals("EN", entity.getEffectiveLanguage());
    }

    @Test
    void saveOrUpdateMutatesExistingRow() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerLanguageRepository repository = new PlayerLanguageRepository(ormContext);
        PlayerLanguageEntity existing = new PlayerLanguageEntity();
        existing.setPlayerId(7L);
        existing.setLanguage("EN");
        existing.setEffectiveLanguage("EN");

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerLanguageEntity.class, 7L)).thenReturn(existing);

        PlayerLanguageEntity updated = repository.saveOrUpdate(7L, "AUTO", "NL");

        assertEquals(existing, updated);
        assertEquals("AUTO", updated.getLanguage());
        assertEquals("NL", updated.getEffectiveLanguage());
    }

    @Test
    void deleteByPlayerIdRemovesExistingEntity() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerLanguageRepository repository = new PlayerLanguageRepository(ormContext);
        PlayerLanguageEntity existing = new PlayerLanguageEntity();
        existing.setPlayerId(9L);

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerLanguageEntity.class, 9L)).thenReturn(existing);

        repository.deleteByPlayerId(9L);

        verify(session).remove(existing);
    }
}
