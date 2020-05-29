/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.FileAppender;
import javafx.application.Platform;
import qupath.lib.gui.logging.LogManager.LogLevel;


/**
 * Logging appender for directing the default Logback output to other text components.
 * 
 * @author Pete Bankhead
 *
 */
class LoggingAppender extends AppenderBase<ILoggingEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(LoggingAppender.class);

	private static LoggingAppender instance;
	private boolean isActive = false;
	private List<TextAppendable> textComponentsFX = Collections.synchronizedList(new ArrayList<>());
	private Level minLevel = Level.INFO;

	private LoggingAppender() {
		super();
		var context = getLoggerContext();
		if (context == null)
			logger.warn("Cannot append logging info without logback!");
		else {
			setName("GUI");
			setContext(context);
			start();
			getRootLogger().addAppender(this);
		}
	}
	
	static Level getLevel(LogLevel logLevel) {
		switch(logLevel) {
		case DEBUG:
			return Level.DEBUG;
		case ERROR:
			return Level.ERROR;
		case INFO:
			return Level.INFO;
		case ALL:
			return Level.ALL;
		case OFF:
			return Level.OFF;
		case TRACE:
			return Level.TRACE;
		case WARN:
			return Level.WARN;
		default:
			return Level.INFO;
		}
	}

	/**
	 * Set the root log level.
	 * @param logLevel
	 */
	void setRootLogLevel(LogLevel logLevel) {
		var root = getRootLogger();
		minLevel = getLevel(logLevel);
		if (root != null)
			root.setLevel(minLevel);
		else
			logger.warn("Cannot get root logger!");
	}
	
	static LoggerContext getLoggerContext() {
		if (LoggerFactory.getILoggerFactory() instanceof LoggerContext) {
			return (LoggerContext)LoggerFactory.getILoggerFactory();
		} else
			return null;
	}
	
	static ch.qos.logback.classic.Logger getRootLogger() {
		var context = getLoggerContext();
		return context == null ? null : context.getLogger(Logger.ROOT_LOGGER_NAME);
	}
	
	
	/**
	 * Send logging messages to the specified file.
	 * 
	 * @param file
	 */
	public void addFileAppender(File file) {
		var context = getLoggerContext();
		if (context != null) {
			FileAppender<ILoggingEvent> appender = new FileAppender<>();
			
			PatternLayoutEncoder encoder = new PatternLayoutEncoder();
			encoder.setContext(context);
			encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] [%-5level] %logger{36} - %msg%n");
			encoder.start();
				    
			appender.setFile(file.getAbsolutePath());
			appender.setContext(context);
			appender.setEncoder(encoder);
			appender.setName(file.getName());
			appender.start();
			context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
		} else
			logger.warn("Cannot append logging info without logback!");
	}

	/**
	 * Get the static {@link LoggingAppender} instance currently used for logging.
	 * @return
	 */
	public synchronized static LoggingAppender getInstance() {
		if (instance == null)
			instance = new LoggingAppender();
		return instance;
	}

	/**
	 * Register a {@link TextAppendable} that will be accept logging events and be updated on the JavaFX Application thread.
	 * @param component the appendable to add
	 */
	public synchronized void addTextAppendableFX(final TextAppendable component) {
		textComponentsFX.add(component);
		isActive = true;
	}

	/**
	 * Deregister a {@link TextAppendable} so that it will no longer be informed of logging events.
	 * @param component the appendable to remove
	 */
	public synchronized void removeTextAppendableFX(final TextAppendable component) {
		textComponentsFX.remove(component);
		isActive = !textComponentsFX.isEmpty();
	}

	@Override
	protected void append(ILoggingEvent event) {
		// Log event if it's important enough
		if (isActive && event.getLevel().isGreaterOrEqual(minLevel)) {
			String message = event.getLevel() + ": " + event.getFormattedMessage() + "\n";
			if (event.getThrowableProxy() != null && event.getLevel().isGreaterOrEqual(Level.ERROR)) {
				for (StackTraceElementProxy element : event.getThrowableProxy().getStackTraceElementProxyArray())
					message += "    " + element.getSTEAsString() + "\n";
				
				if (event.getThrowableProxy().getCause() != null) {
					message += "  Caused by " + event.getThrowableProxy().getCause().getMessage();
					for (StackTraceElementProxy element : event.getThrowableProxy().getCause().getStackTraceElementProxyArray())
						message += "        " + element.getSTEAsString() + "\n";
				}
			}
			// Buffer the next message
			buffer(message);
		}
	}
	
	private boolean flushRequested = false;
	private StringBuffer buffer = new StringBuffer();
	
	private void buffer(String message) {
		synchronized (buffer) {
			buffer.append(message);
			flushBuffer();
		}
	}
	
	private synchronized void flushBuffer() {
		if (Platform.isFxApplicationThread()) {
			String message;
			synchronized(buffer) {
				flushRequested = false;
				message = buffer.toString();
				buffer.setLength(0);
			}
			if (!message.isEmpty()) {
				for (TextAppendable text : textComponentsFX) {
					log(text, message);
				}
			}
		} else {
			if (!flushRequested) {
				flushRequested = true;
				Platform.runLater(() -> flushBuffer());
			}
		}
	}
	
	
	private static void log(final TextAppendable component, final String message) {
		component.appendText(message);
	}

}