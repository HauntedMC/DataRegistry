package nl.hauntedmc.dataregistry.core.lifecycle;

import jakarta.persistence.PersistenceException;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;
import nl.hauntedmc.dataregistry.core.playtime.PlaytimeGamemodeResolver;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.core.service.PlayerActivitySummaryService;
import nl.hauntedmc.dataregistry.core.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.core.service.PlayerNameHistoryService;
import nl.hauntedmc.dataregistry.core.service.PlayerPlaytimeService;
import nl.hauntedmc.dataregistry.core.service.PlayerService;
import nl.hauntedmc.dataregistry.core.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.core.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerLifecycleWriterTest {

    @Test
    void loginRetriesUniqueConstraintRaceAndReturnsSuccess() {
        DataRegistry dataRegistry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        @SuppressWarnings("unchecked")
        Query<Long> outboxQuery = mock(Query.class);

        when(dataRegistry.getORM()).thenReturn(ormContext);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(outboxQuery);
        when(outboxQuery.setParameter(anyString(), any())).thenReturn(outboxQuery);
        when(outboxQuery.setMaxResults(1)).thenReturn(outboxQuery);
        when(outboxQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        String uuid = UUID.randomUUID().toString();
        PlayerEntity player = new PlayerEntity();
        player.setId(7L);
        player.setUuid(uuid);
        player.setUsername("Alice");
        when(playerRepository.findKnownUsername(session, uuid)).thenReturn(Optional.empty());
        when(playerRepository.getOrCreatePlayer(session, uuid, "Alice")).thenReturn(player);

        AtomicInteger attempts = new AtomicInteger();
        PersistenceException constraintFailure =
                new PersistenceException("duplicate key", new SQLException("duplicate key", "23505"));
        RuntimeException transactionFailure = new RuntimeException("Transaction failed", constraintFailure);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ORMContext.TransactionCallback<Object> callback =
                    (ORMContext.TransactionCallback<Object>) invocation.getArgument(0);
            Object result = callback.execute(session);
            if (attempts.getAndIncrement() == 0) {
                throw transactionFailure;
            }
            return result;
        }).when(ormContext).runInTransaction(any());

        PlayerService playerService = new PlayerService(playerRepository, new PlayerIdentityInitializationTracker(), logger);
        PlayerNameHistoryService nameHistoryService = new PlayerNameHistoryService(dataRegistry, logger, 32, false);
        PlayerActivitySummaryService activitySummaryService = new PlayerActivitySummaryService(dataRegistry, logger, false);
        PlayerStatusService statusService = new PlayerStatusService(dataRegistry, logger, 64);
        PlayerConnectionInfoService connectionService = new PlayerConnectionInfoService(
                dataRegistry,
                logger,
                true,
                true,
                45,
                255,
                false
        );
        PlayerSessionService sessionService = new PlayerSessionService(
                dataRegistry,
                logger,
                true,
                true,
                45,
                255,
                64,
                false
        );
        PlayerPlaytimeService playtimeService = new PlayerPlaytimeService(
                dataRegistry,
                logger,
                new PlaytimeGamemodeResolver(PlaytimeTrackingSettings.defaults()),
                64
        );

        PlayerLifecycleWriter writer = new PlayerLifecycleWriter(
                dataRegistry,
                playerService,
                nameHistoryService,
                activitySummaryService,
                statusService,
                connectionService,
                sessionService,
                playtimeService,
                logger,
                2
        );
        LoginCommand command = new LoginCommand(
                "login:" + uuid + ":retry",
                uuid,
                "Alice",
                null,
                null,
                Instant.parse("2026-07-20T07:30:00Z")
        );

        PlayerLifecycleWriteResult result = writer.login(command);

        assertEquals(PlayerLifecycleWriteStatus.SUCCESS, result.status());
        assertNull(result.failure());
        assertTrue(result.identityOptional().isPresent());
        PlayerIdentity identity = result.identityOptional().orElseThrow();
        assertEquals(7L, identity.playerId());
        assertEquals(UUID.fromString(uuid), identity.uuid());
        assertEquals("Alice", identity.username());
        verify(ormContext, times(2)).runInTransaction(any());
        verify(logger).warn(anyString(), any(RuntimeException.class));
    }
}
