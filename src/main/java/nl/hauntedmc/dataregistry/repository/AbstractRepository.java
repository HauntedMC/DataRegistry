package nl.hauntedmc.dataregistry.repository;

import nl.hauntedmc.dataprovider.orm.ORMContext;
import java.util.List;
import java.util.Optional;

public abstract class AbstractRepository<T, ID> implements Repository<T, ID> {

    protected final ORMContext ormContext;
    protected final Class<T> entityClass;

    public AbstractRepository(ORMContext ormContext, Class<T> entityClass) {
        this.ormContext = ormContext;
        this.entityClass = entityClass;
    }

    @Override
    public Optional<T> findById(ID id) {
        return ormContext.runInTransaction(session -> {
            T entity = session.find(entityClass, id);
            return Optional.ofNullable(entity);
        });
    }

    @Override
    public T save(T entity) {
        return ormContext.runInTransaction(session -> {
            session.persist(entity);
            return entity;
        });
    }

    @Override
    public T update(T entity) {
        return ormContext.runInTransaction(session -> session.merge(entity));
    }

    @Override
    public void delete(T entity) {
        ormContext.runInTransaction(session -> {
            session.remove(entity);
            return null;
        });
    }

    @Override
    public void deleteById(ID id) {
        findById(id).ifPresent(this::delete);
    }

    @Override
    public List<T> findAll() {
        return ormContext.runInTransaction(session ->
                session.createQuery("FROM " + entityClass.getSimpleName(), entityClass).list()
        );
    }
}
