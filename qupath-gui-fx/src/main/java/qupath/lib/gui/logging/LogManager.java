/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.logging;

import java.io.File;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

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
	
	private static ObjectProperty<LogLevel> logLevelProperty = new SimpleObjectProperty<>(LogLevel.INFO);
	
	static {
		logLevelProperty.addListener((v, o, n) -> {
			if (n != null)
				LoggingAppender.getInstance().setRootLogLevel(n);
		});
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
//		LoggingAppender.getInstance().setRootLogLevel(level);
		logLevelProperty.set(level);
	}
	
	
	/**
	 * Set the root log level, as set by this manager.
	 * This is not guaranteed to match the actual root log level, in case it has been set elsewhere.
	 * @return 
	 */
	public static LogLevel getRootLogLevel() {
		return logLevelProperty.get();
	}
	
	/**
	 * Property representing the current requested root log level.
	 * @return
	 */
	public static ObjectProperty<LogLevel> rootLogLevelProperty() {
		return logLevelProperty;
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