/*
 * Set the logging level to 'debug'.
 *
 * This will result in more information being logged - which could be useful for
 * debugging... or might just get in the way.
 *
 * Note #1: other logging levels are possible. In order of increasing verbosity, these are:
 *   - Level.ERROR
 *   - Level.WARN
 *   - Level.INFO
 *   - Level.DEBUG
 *   - Level.TRACE
 *
 * Note #2: This assumes that Logback is being used (it is by default with QuPath).
 */


import ch.qos.logback.classic.Level
import org.slf4j.Logger
import org.slf4j.LoggerFactory


def root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
root.setLevel(Level.DEBUG)
println("Logger level: " + root.getLevel())