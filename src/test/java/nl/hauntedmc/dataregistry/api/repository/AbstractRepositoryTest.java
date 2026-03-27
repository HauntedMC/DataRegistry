package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractRepositoryTest {

    @Test
    void constructorRejectsNullDependencies() {
        ORMContext ormContext = mock(ORMContext.class);
        assertThrows(NullPointerException.class, () -> new TestRepository(null));
        assertThrows(NullPointerException.class, () -> new NullEntityClassRepository(ormContext));
    }

    @Test
    void findByIdReturnsOptionalResultFromSession() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        TestRepository repository = new TestRepository(ormContext);
        TestEntity entity = new TestEntity(42L);

        executeTransactionsWithSession(ormContext, session);
        when(session.find(TestEntity.class, 42L)).thenReturn(entity);

        Optional<TestEntity> result = repository.findById(42L);
        assertEquals(Optional.of(entity), result);
    }

    @Test
    void savePersistsEntityAndReturnsIt() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        TestRepository repository = new TestRepository(ormContext);
        TestEntity entity = new TestEntity(1L);

        executeTransactionsWithSession(ormContext, session);

        TestEntity saved = repository.save(entity);

        verify(session).persist(entity);
        assertSame(entity, saved);
    }

    @Test
    void updateReturnsMergedEntity() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        TestRepository repository = new TestRepository(ormContext);
        TestEntity entity = new TestEntity(3L);
        TestEntity merged = new TestEntity(3L);

        executeTransactionsWithSession(ormContext, session);
        when(session.merge(entity)).thenReturn(merged);

        assertSame(merged, repository.update(entity));
    }

    @Test
    void deleteRemovesManagedEntityDirectly() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        TestRepository repository = new TestRepository(ormContext);
        TestEntity entity = new TestEntity(4L);

        executeTransactionsWithSession(ormContext, session);
        when(session.contains(entity)).thenReturn(true);

        repository.delete(entity);

        verify(session).remove(entity);
    }

    @Test
    void deleteMergesBeforeRemoveWhenEntityIsDetached() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        TestRepository repository = new TestRepository(ormContext);
        TestEntity entity = new TestEntity(5L);
        TestEntity managed = new TestEntity(5L);

        executeTransactionsWithSession(ormContext, session);
        when(session.contains(entity)).thenReturn(false);
        when(session.merge(entity)).thenReturn(managed);

        repository.delete(entity);

        verify(session).remove(managed);
    }

    @Test
    void deleteByIdDeletesOnlyWhenEntityExists() {
        TestRepository repository = spy(new TestRepository(mock(ORMContext.class)));
        TestEntity entity = new TestEntity(6L);
        doReturn(Optional.of(entity)).when(repository).findById(6L);
        doReturn(Optional.empty()).when(repository).findById(999L);
        doNothing().when(repository).delete(entity);

        repository.deleteById(6L);
        repository.deleteById(999L);

        verify(repository).delete(entity);
    }

    @Test
    void findAllReturnsListFromSimpleEntityQuery() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<TestEntity> query = mock(Query.class);
        TestRepository repository = new TestRepository(ormContext);
        List<TestEntity> expected = List.of(new TestEntity(1L), new TestEntity(2L));

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery("FROM TestEntity", TestEntity.class)).thenReturn(query);
        when(query.list()).thenReturn(expected);

        List<TestEntity> result = repository.findAll();

        assertSame(expected, result);
    }

    private static class TestRepository extends AbstractRepository<TestEntity, Long> {
        private TestRepository(ORMContext ormContext) {
            super(ormContext, TestEntity.class);
        }
    }

    private static class NullEntityClassRepository extends AbstractRepository<TestEntity, Long> {
        private NullEntityClassRepository(ORMContext ormContext) {
            super(ormContext, null);
        }
    }

    private record TestEntity(Long id) {
    }
}
