package nl.hauntedmc.dataregistry.platform.bukkit.logger;

import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BukkitLoggerAdapterTest {

    @Test
    void constructorRejectsNullLogger() {
        assertThrows(NullPointerException.class, () -> new BukkitLoggerAdapter(null));
    }

    @Test
    void delegatesAllLoggingMethodsWithExpectedLevels() {
        Logger logger = mock(Logger.class);
        BukkitLoggerAdapter adapter = new BukkitLoggerAdapter(logger);
        RuntimeException error = new RuntimeException("boom");

        adapter.info("info");
        adapter.warn("warn");
        adapter.error("error");
        adapter.info("info-ex", error);
        adapter.warn("warn-ex", error);
        adapter.error("error-ex", error);

        verify(logger).log(Level.INFO, "info");
        verify(logger).log(Level.WARNING, "warn");
        verify(logger).log(Level.SEVERE, "error");
        verify(logger).log(Level.INFO, "info-ex", error);
        verify(logger).log(Level.WARNING, "warn-ex", error);
        verify(logger).log(Level.SEVERE, "error-ex", error);
    }
}
