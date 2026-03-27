package nl.hauntedmc.dataregistry.api.repository;

import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {

    /**
     * Finds an entity by id.
     */
    Optional<T> findById(ID id);

    /**
     * Persists a new entity.
     */
    T save(T entity);

    /**
     * Updates an existing entity and returns the managed result.
     */
    T update(T entity);

    /**
     * Deletes the provided entity.
     */
    void delete(T entity);

    /**
     * Deletes an entity by id when present.
     */
    void deleteById(ID id);

    /**
     * Returns all entities of this repository type.
     */
    List<T> findAll();
}
