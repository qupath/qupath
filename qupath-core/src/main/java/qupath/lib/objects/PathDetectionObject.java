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

package qupath.lib.objects;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementList.MeasurementListType;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

/**
 * A detection PathObject.
 * <p>
 * Detections tend to be very numerous (e.g. millions of nuclei, each one a detection), and so need to be 
 * represented and displayed very efficiently.
 * 
 * @author Pete Bankhead
 * 
 * @see PathAnnotationObject
 */
public class PathDetectionObject extends PathROIObject {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Default constructor. Should not be used directly, instead use {@link PathObjects#createDetectionObject(ROI)}.
	 */
	public PathDetectionObject() {
		super();
	}
	
	/**
	 * Create a detection object.
	 * 
	 * @param pathROI
	 * @param pathClass
	 * @param measurements
	 */
	PathDetectionObject(ROI pathROI, PathClass pathClass, MeasurementList measurements) {
		super(pathROI, pathClass, measurements);
	}

	/**
	 * Create a new PathDetectionObject with a float measurement list.
	 * 
	 * @param pathROI
	 * @param pathClass
	 */
	protected PathDetectionObject(ROI pathROI, PathClass pathClass) {
		super(pathROI, pathClass);
	}
	
	PathDetectionObject(ROI pathROI) {
		this(pathROI, null);
	}
	
	/**
	 * Always returns false - detection objects shouldn't be edited.
	 */
	@Override
	public boolean isEditable() {
		return false;
	}
	
	/**
	 * Default to a simple, float measurement list.
	 */
	@Override
	protected MeasurementList createEmptyMeasurementList() {
		return MeasurementListFactory.createMeasurementList(0, MeasurementListType.FLOAT);
	}
	
}
