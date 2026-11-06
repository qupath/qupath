/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

import org.slf4j.LoggerFactory;
import qupath.lib.progress.LogProgressMonitor;
import qupath.lib.progress.ProgressMonitor;

/**
 * A PluginRunner that simply logs progress and output.
 * <p>
 * This doesn't need to be run on any particular thread (e.g. the JavaFX Application thread).
 * 
 * @author Pete Bankhead
 */
public class CommandLineTaskRunner extends AbstractTaskRunner {
	
	/**
	 * Constructor for a PluginRunner that send progress to a log, and runs tasks using the default number of threads
	 */
	public CommandLineTaskRunner() {
		super();
	}

	/**
	 * Constructor for a PluginRunner that send progress to a log.
	 * @param nThreads the number of threads to use when running tasks
	 */
	public CommandLineTaskRunner(int nThreads) {
		super(nThreads);
	}

	@Override
	public ProgressMonitor makeProgressMonitor() {
		// TODO: CHOOSE A BETTER LOGGER!
		return LogProgressMonitor.info(LoggerFactory.getLogger(CommandLineTaskRunner.class));
	}
	

}