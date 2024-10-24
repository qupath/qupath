/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

import java.util.Collection;

/**
 * A minimal interface for a class capable of running tasks in parallel, giving feedback to the user.
 */
public interface TaskRunner {

	/**
	 * Query if the plugin can be cancelled while running.
	 * Plugins are expected to check this flag before time-consuming operations.
	 * @return
	 */
	boolean isCancelled();

	/**
	 * Pass a collection of parallelizable tasks to run.
	 * @param tasks the tasks to run. If these are instances of {@link PathTask} then 
	 *              an optional postprocessing may be applied after all tasks are complete.
	 */
	default void runTasks(Collection<? extends Runnable> tasks) {
		runTasks(null, tasks);
	}

	/**
	 * Pass a collection of parallelizable tasks to run.
	 * @param message optional message to display to the user when running tasks; may be null
	 * @param tasks the tasks to run. If these are instances of {@link PathTask} then
	 *              an optional postprocessing may be applied after all tasks are complete.
	 * @since v0.6.0
	 */
	void runTasks(String message, Collection<? extends Runnable> tasks);

}