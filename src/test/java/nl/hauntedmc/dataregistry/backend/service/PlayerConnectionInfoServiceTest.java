package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerConnectionInfoServiceTest {

    @Test
    void constructorRejectsInvalidArguments() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerConnectionInfoService(registry, logger, true, true, 6, 255)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerConnectionInfoService(registry, logger, true, true, 45, 0)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PlayerConnectionInfoService(null, logger, true, true, 45, 255)
        );
    }

    @Test
    void updateOnLoginCreatesEntityAndSanitizesFields() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerConnectionInfoService service = new PlayerConnectionInfoService(
                registry, logger, true, true, 12, 8
        );
        PlayerEntity player = persistedPlayer();
        PlayerEntity managed = persistedPlayer();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(managed);
        when(session.find(PlayerConnectionInfoEntity.class, managed.getId())).thenReturn(null);

        service.updateOnLogin(player, "  192.168.100.200  ", "  very-long-host-name  ");

        ArgumentCaptor<PlayerConnectionInfoEntity> captor = ArgumentCaptor.forClass(PlayerConnectionInfoEntity.class);
        verify(session).persist(captor.capture());
        PlayerConnectionInfoEntity info = captor.getValue();
        assertEquals(managed, info.getPlayer());
        assertEquals("192.168.100.", info.getIpAddress());
        assertEquals("very-lon", info.getVirtualHost());
        assertNotNull(info.getFirstConnectionAt());
        assertNotNull(info.getLastConnectionAt());
    }

    @Test
    void updateOnLoginUpdatesExistingEntity() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerConnectionInfoService service = new PlayerConnectionInfoService(
                registry, logger, true, false, 45, 255
        );
        PlayerEntity player = persistedPlayer();
        PlayerConnectionInfoEntity info = new PlayerConnectionInfoEntity();

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(player);
        when(session.find(PlayerConnectionInfoEntity.class, player.getId())).thenReturn(info);

        service.updateOnLogin(player, "127.0.0.1", "mc.example.net");

        assertNotNull(info.getFirstConnectionAt());
        assertNotNull(info.getLastConnectionAt());
        assertEquals("127.0.0.1", info.getIpAddress());
        assertNull(info.getVirtualHost());
    }

    @Test
    void updateOnDisconnectCreatesOrUpdatesDisconnectTimestamp() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerConnectionInfoService service = new PlayerConnectionInfoService(
                registry, logger, false, false, 45, 255
        );
        PlayerEntity player = persistedPlayer();
        PlayerConnectionInfoEntity existing = new PlayerConnectionInfoEntity();
        existing.setIpAddress("stored-ip");
        existing.setVirtualHost("stored-host");

        when(registry.getORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.merge(player)).thenReturn(player);
        when(session.find(PlayerConnectionInfoEntity.class, player.getId()))
                .thenReturn(null)
                .thenReturn(existing);

        service.updateOnDisconnect(player);
        service.updateOnDisconnect(player);

        ArgumentCaptor<PlayerConnectionInfoEntity> captor = ArgumentCaptor.forClass(PlayerConnectionInfoEntity.class);
        verify(session).persist(captor.capture());
        assertNotNull(captor.getValue().getLastDisconnectAt());
        assertNotNull(existing.getLastDisconnectAt());
        assertNull(existing.getIpAddress());
        assertNull(existing.getVirtualHost());
    }

    @Test
    void invalidEntityAndRuntimeFailureAreLogged() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        PlayerConnectionInfoService service = new PlayerConnectionInfoService(
                registry, logger, true, true, 45, 255
        );

        service.updateOnLogin(new PlayerEntity(), "1.1.1.1", "host");
        service.updateOnDisconnect(new PlayerEntity());
        verify(logger).warn("updateOnLogin called with an invalid player entity.");
        verify(logger).warn("updateOnDisconnect called with an invalid player entity.");

        when(registry.getORM()).thenReturn(ormContext);
        doThrow(new RuntimeException("tx failed")).when(ormContext).runInTransaction(any());
        PlayerEntity player = persistedPlayer();
        player.setUuid("uuid\nvalue");

        service.updateOnLogin(player, "1.1.1.1", "host");
        service.updateOnDisconnect(player);

        verify(logger, times(2)).error(contains("uuid_value"), any(RuntimeException.class));
    }

    private static PlayerEntity persistedPlayer() {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setUuid("71af55ca-c310-45b2-acf8-94efd8731d81");
        player.setUsername("Alice");
        return player;
    }
}
