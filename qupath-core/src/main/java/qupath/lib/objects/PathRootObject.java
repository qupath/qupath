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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.roi.interfaces.ROI;

/**
 * The root object used at the base of a PathObjectHierarchy.
 * <p>
 * This doesn't have a ROI, and can't have its PathClass set.
 * 
 * @author Pete Bankhead
 *
 */
public class PathRootObject extends PathObject {

	private static final long serialVersionUID = 1L;
	
	final private static Logger logger = LoggerFactory.getLogger(PathRootObject.class);
	
	@Override
	public boolean isRootObject() {
		return true;
	}

	@Override
	public PathClass getPathClass() {
		return PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMAGE_ROOT);
	}

	@Override
	public void setPathClass(PathClass pathClass, double classProbability) {
		logger.warn("Attempted to set class for unclasifiable object - will be ignored");
	}

	@Override
	public double getClassProbability() {
		return Double.NaN;
	}

	@Override
	public ROI getROI() {
		return null;
	}
	
	@Override
	public String toString() {
		return "Image";
	}
	
	/**
	 * The root object is never locked.
	 */
	@Override
	public boolean isEditable() {
		return false;
	}
	
}
