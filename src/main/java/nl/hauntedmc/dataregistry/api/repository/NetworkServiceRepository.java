package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Optional;

public class NetworkServiceRepository extends AbstractRepository<NetworkServiceEntity, Long> {

    public NetworkServiceRepository(ORMContext ormContext) {
        super(ormContext, NetworkServiceEntity.class);
    }

    public Optional<NetworkServiceEntity> findByKindAndName(ServiceKind kind, String serviceName) {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM NetworkServiceEntity s " +
                                        "WHERE s.serviceKind = :kind AND s.serviceName = :serviceName",
                                NetworkServiceEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", serviceName)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }
}
