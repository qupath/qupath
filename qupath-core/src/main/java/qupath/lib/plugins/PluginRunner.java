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
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Implementing classes encapsulate the data and functionality needed to run a plugin on a single image.
 * 
 * This means access to an ImageData object (along with helper methods to access its server, hierarchy &amp;
 * selected objects), as well as the ability to run a collection of tasks - possibly in parallel.
 * 
 * This implementation may also (optionally) provide useful feedback on progress when running tasks.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface PluginRunner<T> {

	boolean isBatchMode();

	ImageData<T> getImageData();

	ImageServer<T> getImageServer();
	
	boolean isCancelled();

	PathObjectHierarchy getHierarchy();

	PathObject getSelectedObject();

	void runTasks(Collection<Runnable> tasks);

}