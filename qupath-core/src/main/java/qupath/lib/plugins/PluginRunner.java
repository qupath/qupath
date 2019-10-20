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

package qupath.lib.plugins;

import java.util.Collection;

import qupath.lib.images.ImageData;

/**
 * Implementing classes encapsulate the data and functionality needed to run a plugin on a single image.
 * <p>
 * This means access to an ImageData object (along with helper methods to access its server, hierarchy &amp;
 * selected objects), as well as the ability to run a collection of tasks - possibly in parallel.
 * <p>
 * This implementation may also (optionally) provide useful feedback on progress when running tasks.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface PluginRunner<T> {

	/**
	 * Get the current {@link ImageData} upon which the plugin should operate.
	 * @return
	 */
	ImageData<T> getImageData();

	/**
	 * Query if the plugin should be cancelled while running.
	 * Plugins are expected to check this flag before time-consuming operations.
	 * @return
	 */
	boolean isCancelled();

	/**
	 * Pass a collection of parallelizable tasks to run.
	 * @param tasks the tasks to run. If these are instances of {@link PathTask} then 
	 *              an optional postprocessing may be applied after all tasks are complete.
	 * @param fireHierarchyUpdate if true, a hierarchy update should be fired on completion. 
	 *                            This means that individual tasks do not need to fire their own updates,
	 *                            which can be a performance bottleneck.
	 */
	void runTasks(Collection<Runnable> tasks, boolean fireHierarchyUpdate);

}