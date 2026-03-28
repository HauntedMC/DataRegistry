package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Optional;

public class ServiceInstanceRepository extends AbstractRepository<ServiceInstanceEntity, Long> {

    public ServiceInstanceRepository(ORMContext ormContext) {
        super(ormContext, ServiceInstanceEntity.class);
    }

    public Optional<ServiceInstanceEntity> findByInstanceId(String instanceId) {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i WHERE i.instanceId = :instanceId",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("instanceId", instanceId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }
}
