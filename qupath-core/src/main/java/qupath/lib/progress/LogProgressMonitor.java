package qupath.lib.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Objects;

/**
 * Progress monitor that writes log messages.
 */
public class LogProgressMonitor implements ProgressMonitor {

    private final Logger logger;
    private final Level level;

    public LogProgressMonitor(Class<?> cls, Level level) {
        this(LoggerFactory.getLogger(cls), level);
    }

    public LogProgressMonitor(Logger logger, Level level) {
        Objects.requireNonNull(logger, "Logger must not be null.");
        Objects.requireNonNull(level, "Level must not be null.");
        this.logger = logger;
        this.level = level;
    }

    @Override
    public void updateProgress(String message, double progress) {
        if (isIndeterminate(progress))
            logger.atLevel(level).log(message);
        else
            logger.atLevel(level).log("{} ({}%)", message, Math.round(progress * 100.0));
    }

    public static LogProgressMonitor debug(Logger logger) {
        return create(logger, Level.DEBUG);
    }

    public static LogProgressMonitor trace(Logger logger) {
        return create(logger, Level.TRACE);
    }

    public static LogProgressMonitor info(Logger logger) {
        return create(logger, Level.INFO);
    }

    public static LogProgressMonitor create(Class<?> cls, Level level) {
        return create(LoggerFactory.getLogger(cls), level);
    }

    public static LogProgressMonitor create(Logger logger, Level level) {
        return new LogProgressMonitor(logger, level);
    }

}
