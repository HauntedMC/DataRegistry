package nl.hauntedmc.dataregistry.platform.velocity.logger;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SLF4JLoggerAdapterTest {

    @Test
    void delegatesAllLoggingMethodsToSlf4jLogger() {
        Logger logger = mock(Logger.class);
        SLF4JLoggerAdapter adapter = new SLF4JLoggerAdapter(logger);
        RuntimeException error = new RuntimeException("boom");

        adapter.info("info");
        adapter.warn("warn");
        adapter.error("error");
        adapter.info("info-ex", error);
        adapter.warn("warn-ex", error);
        adapter.error("error-ex", error);

        verify(logger).info("info");
        verify(logger).warn("warn");
        verify(logger).error("error");
        verify(logger).info("info-ex", error);
        verify(logger).warn("warn-ex", error);
        verify(logger).error("error-ex", error);
    }
}
