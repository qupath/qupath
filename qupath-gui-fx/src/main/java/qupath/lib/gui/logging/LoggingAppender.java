/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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


/**
 * Logging appender for redirecting the default Logback output to an internally-accessible area.
 * 
 * TODO: Look for a more suitable solution in the event that another logging system is used...
 * 
 * @author Pete Bankhead
 *
 */
public class LoggingAppender extends AppenderBase<ILoggingEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(LoggingAppender.class);

	private static LoggingAppender instance;
	private boolean isActive = false;
	private List<TextAppendable> textComponentsFX = Collections.synchronizedList(new ArrayList<>());
	private Level minLevel = Level.INFO;

	private LoggingAppender() {
		super();
		if (LoggerFactory.getILoggerFactory() instanceof LoggerContext) {
			LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
			setName("GUI");
			setContext(context);
			start();
			context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
		} else
			logger.warn("Cannot append logging info without logback!");
	}

	
	/**
	 * Send logging messages to the specified file.
	 * 
	 * @param file
	 */
	public void addFileAppender(File file) {
		if (LoggerFactory.getILoggerFactory() instanceof LoggerContext) {
			LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
			FileAppender appender = new FileAppender<>();
			
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


	public synchronized static LoggingAppender getInstance() {
		if (instance == null)
			instance = new LoggingAppender();
		return instance;
	}

	public void addTextComponent(final TextAppendable component) {
		textComponentsFX.add(component);
		isActive = true;
	}

	public void removeTextComponent(final TextAppendable component) {
		textComponentsFX.remove(component);
		isActive = !textComponentsFX.isEmpty();
	}

	@Override
	protected void append(ILoggingEvent event) {
		// Log event if it's important enough
		if (isActive && event.getLevel().isGreaterOrEqual(minLevel)) {
			final boolean isError = event.getLevel().isGreaterOrEqual(Level.WARN);
			String tempMessage = event.getLevel() + ": " + event.getFormattedMessage() + "\n";
			if (event.getThrowableProxy() != null && event.getLevel().isGreaterOrEqual(Level.ERROR)) {
				for (StackTraceElementProxy element : event.getThrowableProxy().getStackTraceElementProxyArray())
					tempMessage += "    " + element.getSTEAsString() + "\n";
				
				if (event.getThrowableProxy().getCause() != null) {
					tempMessage += "  Caused by " + event.getThrowableProxy().getCause().getMessage();
					for (StackTraceElementProxy element : event.getThrowableProxy().getCause().getStackTraceElementProxyArray())
						tempMessage += "        " + element.getSTEAsString() + "\n";
				}
				
//				for (StackTraceElement element : event.getCallerData())
//					tempMessage += "    TRACED: " + element.toString() + "\n";
//				tempMessage += "MESSAGE: " + event.getThrowableProxy().getMessage() + "\n";
			}
			
			final String message = tempMessage;
			synchronized(textComponentsFX) {
				for (TextAppendable text : textComponentsFX) {
					if (Platform.isFxApplicationThread())
						log(text, message, isError);
					else
						Platform.runLater(() -> log(text, message, isError));
				}
			}
			
		}
	}
	
	
	private static void log(final TextAppendable component, final String message, final boolean isError) {
		component.appendText(message);
//		if (isError)
//			component.setStyle( "-fx-text-fill: red" );
//		else
//			component.setStyle( "-fx-text-fill: blue" );
//		Text text = new Text(message);
//		if (isError)
//			text.setFill(javafx.scene.paint.Color.RED);
//		component.getChildren().add(text);
//		String current = component.get();
//		if (current == null)
//			component.set(message);
//		else
//			component.set(current + message);
	}

}
