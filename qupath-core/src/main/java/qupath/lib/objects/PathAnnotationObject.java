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
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

/**
 * An annotation PathObject.
 * <p>
 * These tend to be larger and less common than PathDetectionObjects.
 * <p>
 * As such, they can be represented and displayed with more emphasis on flexibility, rather than efficiency.
 * 
 * @author Pete Bankhead
 *
 * @see PathDetectionObject
 *
 */
public class PathAnnotationObject extends PathROIObject {
	
	private static final long serialVersionUID = 1L;
	
	private final static String KEY_ANNOTATION_TEXT = "ANNOTATION_DESCRIPTION";
	
	/**
	 * Default constructor. Should not be used directly, instead use {@link PathObjects#createAnnotationObject(ROI)}.
	 */
	public PathAnnotationObject() {
		super();
	}

	PathAnnotationObject(ROI pathROI) {
		super(pathROI, null);
	}
	
	PathAnnotationObject(ROI pathROI, PathClass pathClass) {
		super(pathROI, pathClass);
	}
	
	PathAnnotationObject(ROI pathROI, PathClass pathClass, MeasurementList measurements) {
		super(pathROI, pathClass, measurements);
	}
	
	/**
	 * Set a free text description for this annotation.
	 * 
	 * @param text
	 */
	public void setDescription(final String text) {
		// Don't store unless we need to (which can also help avoid creating unnecessary metadata stores)
		Object existing = retrieveMetadataValue(KEY_ANNOTATION_TEXT);
		if (text == null && existing == null || text.equals(existing))
			return;
		this.storeMetadataValue(KEY_ANNOTATION_TEXT, text);
	}

	/**
	 * Get a free text description previously set for this annotation.
	 */
	public String getDescription() {
		return (String)retrieveMetadataValue(KEY_ANNOTATION_TEXT);
	}

//	PathAnnotationObject(PathAnnotationObject pathObject, PathROI pathROI) {
//		super(pathROI, pathObject.getPathClass(), pathObject.getMeasurementList());
//	}

}
