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

package qupath.lib.objects;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

/**
 * A subclass of PathDetectionObject, generally used to represent an image region that doesn't 
 * (in itself) correspond to any particularly interesting structure. 
 * Examples include square tiles or irregularly-shaped 'superpixels'.
 * 
 * @author Pete Bankhead
 *
 * @see PathDetectionObject
 */
public class PathTileObject extends PathDetectionObject {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Default constructor. Should not be used directly, instead use {@link PathObjects#createTileObject(ROI)}.
	 */
	public PathTileObject() {
		super();
	}

	protected PathTileObject(ROI pathROI) {
		super(pathROI, null);
	}
	
	PathTileObject(ROI pathROI, MeasurementList measurements) {
		super(pathROI, null, measurements);
	}
	
	PathTileObject(ROI pathROI, PathClass pathClass, MeasurementList measurements) {
		super(pathROI, pathClass, measurements);
	}
		
		
}
