package nl.hauntedmc.dataregistry.platform.velocity.listener;

import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriteResult;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriteStatus;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RetainedPlayerLifecycleCommandQueueTest {

    @Test
    void retainsTransientFailureAndPreventsLaterCommandFromOvertakingIt() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduledFuture = mock(ScheduledFuture.class);
        List<Runnable> scheduledRetries = new ArrayList<>();
        doAnswer(invocation -> {
            scheduledRetries.add(invocation.getArgument(0));
            return scheduledFuture;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS));

        List<String> writes = new ArrayList<>();
        AtomicInteger loginAttempts = new AtomicInteger();
        AtomicInteger recoveryCallbacks = new AtomicInteger();
        RetainedPlayerLifecycleCommandQueue queue = new RetainedPlayerLifecycleCommandQueue(
                Runnable::run,
                scheduler,
                mock(ILoggerAdapter.class),
                recoveryCallbacks::incrementAndGet
        );
        String uuid = UUID.randomUUID().toString();

        queue.submit(
                uuid,
                "login-event",
                () -> {
                    writes.add("login");
                    return loginAttempts.getAndIncrement() == 0
                            ? PlayerLifecycleWriteResult.failure(
                                    "login-event",
                                    PlayerLifecycleWriteStatus.TRANSIENT_FAILURE,
                                    new RuntimeException("database unavailable")
                            )
                            : PlayerLifecycleWriteResult.success("login-event", null);
                },
                ignored -> {
                },
                ignored -> {
                }
        );
        queue.submit(
                uuid,
                "transfer-event",
                () -> {
                    writes.add("transfer");
                    return PlayerLifecycleWriteResult.success("transfer-event", null);
                },
                ignored -> {
                },
                ignored -> {
                }
        );

        assertEquals(List.of("login"), writes);
        assertEquals(1, scheduledRetries.size());
        assertTrue(queue.hasPendingCommand(uuid));

        scheduledRetries.removeFirst().run();

        assertEquals(List.of("login", "login", "transfer"), writes);
        assertFalse(queue.hasPendingCommand(uuid));
        assertEquals(1, recoveryCallbacks.get());
    }

    @Test
    void exposesPermanentFailureWithoutPretendingTheCommandSucceeded() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        RetainedPlayerLifecycleCommandQueue queue = new RetainedPlayerLifecycleCommandQueue(
                Runnable::run,
                scheduler,
                logger,
                () -> {
                }
        );
        String uuid = UUID.randomUUID().toString();
        RuntimeException failure = new RuntimeException("invalid lifecycle data");

        PlayerLifecycleWriteResult result = queue.submit(
                uuid,
                "disconnect-event",
                () -> PlayerLifecycleWriteResult.failure(
                        "disconnect-event",
                        PlayerLifecycleWriteStatus.PERMANENT_FAILURE,
                        failure
                ),
                ignored -> {
                },
                ignored -> {
                }
        ).join();

        assertEquals(PlayerLifecycleWriteStatus.PERMANENT_FAILURE, result.status());
        assertEquals(failure, result.failure());
        assertFalse(queue.hasPendingCommand(uuid));
        assertNotNull(queue.terminalFailure(uuid));
        assertEquals("disconnect-event", queue.terminalFailure(uuid).eventId());
        verify(logger).error(any(String.class), eq(failure));
    }
}
