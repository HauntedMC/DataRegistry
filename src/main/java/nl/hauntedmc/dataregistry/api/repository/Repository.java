package nl.hauntedmc.dataregistry.api.repository;

import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {

    Optional<T> findById(ID id);

    T save(T entity);

    T update(T entity);

    void delete(T entity);

    void deleteById(ID id);

    List<T> findAll();
}
