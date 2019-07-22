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

import java.io.IOException;
import java.util.Collection;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.interfaces.ROI;

/**
 * Minimal interface that may be used to plugins that perform detection within a specified ROI 
 * and using a specified ImageData with set parameters.
 * <p>
 * This enables new detection plugins to be written with somewhat less boilerplate code.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface ObjectDetector<T> {
	
	/**
	 * Detect objects.
	 * 
	 * @param imageData the {@link ImageData} for which objects should be detected
	 * @param params optional list of parameters required for the detection
	 * @param roi specific region within which the detection should be applied
	 * @return
	 * @throws IOException
	 */
	public Collection<PathObject> runDetection(ImageData<T> imageData, ParameterList params, ROI roi) throws IOException;

	/**
	 * Get a String summarizing the result, which may be displayed to a user or logged.
	 * @return
	 */
	public String getLastResultsDescription();

}
