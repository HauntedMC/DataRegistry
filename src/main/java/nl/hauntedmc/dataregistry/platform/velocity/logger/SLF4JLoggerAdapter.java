package nl.hauntedmc.dataregistry.platform.velocity.logger;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.slf4j.Logger;

import java.util.Objects;

public class SLF4JLoggerAdapter implements ILoggerAdapter {
    private final Logger logger;

    public SLF4JLoggerAdapter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        logger.info(message, throwable);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        logger.warn(message, throwable);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
