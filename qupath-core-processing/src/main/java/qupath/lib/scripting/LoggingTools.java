/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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


package qupath.lib.scripting;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;


public class LoggingTools {
	
	private static final Logger defaultLogger = LoggerFactory.getLogger(LoggingTools.class);
	
	/**
	 * Create a {@link Writer} that passes messages to the log at a specified level.
	 * @param logger the logger to use; if null, a default logger will be used
	 * @param level the logging level
	 * @return
	 */
	public static Writer createLogWriter(Logger logger, Level level) {
		Objects.requireNonNull(level);
		var localLogger = logger == null ? defaultLogger : logger;
		if (level == Level.INFO)
			return new LoggerInfoWriter(localLogger);
		if (level == Level.ERROR)
			return new LoggerErrorWriter(localLogger);
		return new LoggerWriter(localLogger, level);
	}

	
	private static class LoggerWriter extends Writer {
		
		protected Logger logger;
		protected Level level;
		
		private LoggerWriter(Logger logger, Level level) {
			this.level = level;
		}

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			// Don't need to log newlines
			if (len == 1 && cbuf[off] == '\n')
				return;
			String s = String.valueOf(cbuf, off, len);
			// Skip newlines on Windows too...
			if (s.equals(System.lineSeparator()))
				return;
			log(s);
		}
		
		protected void log(String s) {
			logger.atLevel(level).log(s);
		}

		@Override
		public void flush() throws IOException {}
		
		@Override
		public void close() throws IOException {
			flush();
		}
	}
	
	
	private static class LoggerInfoWriter extends LoggerWriter {
		
		private LoggerInfoWriter(Logger logger) {
			super(logger, Level.INFO);
		}

		@Override
		protected void log(String s) {
			logger.info(s);
		}
	}
	
	private static class LoggerErrorWriter extends LoggerWriter {
		
		private LoggerErrorWriter(Logger logger) {
			super(logger, Level.ERROR);
		}

		@Override
		protected void log(String s) {
			logger.error(s);
		}
	}

}
