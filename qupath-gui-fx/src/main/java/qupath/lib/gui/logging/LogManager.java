package qupath.lib.gui.logging;

import java.io.File;

/**
 * Manage logging levels.
 * 
 * @author Pete Bankhead
 */
public class LogManager {
	
	/**
	 * Available log levels.
	 */
	public static enum LogLevel {
		/**
		 * Trace logging (an awful lot of messages)
		 */
		TRACE,
		/**
		 * Debug logging (a lot of messages)
		 */
		DEBUG,
		/**
		 * Info logging (default)
		 */
		INFO,
		/**
		 * Warn logging (only if something is moderately important)
		 */
		WARN,
		/**
		 * Error logging (only if something goes recognizably wrong)
		 */
		ERROR,
		/**
		 * All log messages
		 */
		ALL,
		/**
		 * Turn off logging
		 */
		OFF;
	}
	
	/**
	 * Request logging to the specified file.
	 * @param file
	 */
	public static void logToFile(File file) {
		LoggingAppender.getInstance().addFileAppender(file);
	}
	
	/**
	 * Set the root log level.
	 * @param level
	 */
	public static void setRootLogLevel(LogLevel level) {
		LoggingAppender.getInstance().setRootLogLevel(level);
	}
	
	/**
	 * Set the root log level to DEBUG.
	 */
	public static void setDebug() {
		setRootLogLevel(LogLevel.DEBUG);
	}
	
	/**
	 * Set the root log level to DEBUG.
	 */
	public static void setInfo() {
		setRootLogLevel(LogLevel.INFO);
	}
	
	/**
	 * Set the root log level to LogLevel.WARN.
	 */
	public static void setWarn() {
		setRootLogLevel(LogLevel.WARN);
	}
	
	/**
	 * Set the root log level to LogLevel.ERROR.
	 */
	public static void setError() {
		setRootLogLevel(LogLevel.ERROR);
	}
	
	/**
	 * Set the root log level to LogLevel.TRACE.
	 */
	public static void setTrace() {
		setRootLogLevel(LogLevel.TRACE);
	}
	
	/**
	 * Set the root log level to LogLevel.ALL.
	 */
	public static void setAll() {
		setRootLogLevel(LogLevel.ALL);
	}
	
	/**
	 * Set the root log level to LogLevel.OFF.
	 */
	public static void setOff() {
		setRootLogLevel(LogLevel.OFF);
	}
	
	
	/**
	 * Register a {@link TextAppendable} that will be accept logging events and be updated on the JavaFX Application thread.
	 * @param component the appendable to add
	 */
	public static void addTextAppendableFX(final TextAppendable component) {
		LoggingAppender.getInstance().addTextAppendableFX(component);
	}

	/**
	 * Deregister a {@link TextAppendable} so that it will no longer be informed of logging events.
	 * @param component the appendable to remove
	 */
	public static void removeTextAppendableFX(final TextAppendable component) {
		LoggingAppender.getInstance().removeTextAppendableFX(component);
	}
	
	

}