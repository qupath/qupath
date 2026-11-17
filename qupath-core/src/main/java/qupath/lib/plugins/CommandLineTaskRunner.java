/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import qupath.lib.progress.LogProgressMonitor;
import qupath.lib.progress.ProgressMonitor;

import java.util.Objects;

/**
 * A TaskRunner that simply logs progress and output.
 * <p>
 * This doesn't need to be run on any particular thread (e.g. the JavaFX Application thread).
 * Instances are immutable; the number of threads, logger and logging level can be adjusted
 * by calling the instance methods that create new task runners.
 */
public class CommandLineTaskRunner extends AbstractTaskRunner {

	private final int nThreads;
	private final Logger logger;
	private final Level logLevel ;

	private CommandLineTaskRunner() {
		this(-1, LoggerFactory.getLogger(CommandLineTaskRunner.class), Level.INFO);
	}

	private CommandLineTaskRunner(int nThreads, Logger logger, Level logLevel) {
		super(nThreads);
		this.nThreads = nThreads;
		this.logger = logger;
		this.logLevel = logLevel;
	}

	/**
	 * Create a new task runner. To customize this, call the other instance methods that create
	 * task runners with specific properties.
	 * @return the new task runner
	 */
	public static CommandLineTaskRunner create() {
		return new CommandLineTaskRunner();
	}

	/**
	 * Create a new task runner that uses the specified logger.
	 * @param logger the logger to use, or {@code null} if no logging should be done.
	 * @return the new task runner
	 */
	public CommandLineTaskRunner logger(Logger logger) {
		return new CommandLineTaskRunner(this.nThreads, logger, this.logLevel);
	}

	/**
	 * Create a new task runner that uses the specified number of threads.
	 * @return the new task runner
	 */
	public CommandLineTaskRunner nThreads(int nThreads) {
		return new CommandLineTaskRunner(nThreads, this.logger, this.logLevel);
	}

	/**
	 * Create a new task runner that logs at the info level.
	 * @return the new task runner
	 */
	public CommandLineTaskRunner info() {
		return logLevel(Level.INFO);
	}

	/**
	 * Create a new task runner that logs at the debug level.
	 * @return the new task runner
	 */
	public CommandLineTaskRunner debug() {
		return logLevel(Level.DEBUG);
	}

	/**
	 * Create a new task runner that logs at the trace level.
	 * @return the new task runner
	 */
	public CommandLineTaskRunner trace() {
		return logLevel(Level.TRACE);
	}

	/**
	 * Create a new task runner that logs at the specified level.
	 * @param level the log level; if {@code null} (or the logger is null) no logging will be done.
	 * @return the new task runner
	 */
	public CommandLineTaskRunner logLevel(Level level) {
		return new CommandLineTaskRunner(this.nThreads, this.logger, level);
	}

	@Override
	public ProgressMonitor makeProgressMonitor() {
		if (logger == null || logLevel == null) {
			return ProgressMonitor.NULL;
		} else {
			return LogProgressMonitor.create(logger, logLevel);
		}
	}
	

}