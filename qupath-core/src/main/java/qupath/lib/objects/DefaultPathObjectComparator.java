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

import java.util.Comparator;
import java.util.Objects;

import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.DefaultROIComparator;

/**
 * Default comparator to enable objects to be sorted in a more predictable manner.
 * <p>
 * The aim is to help sorted lists to keep non-detection objects near the top, 
 * and thereafter to sort by ROI location (y coordinate first, then x) according to
 * the DefaultROIComparator.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultPathObjectComparator implements Comparator<PathObject> {
	
	private static final DefaultPathObjectComparator instance = new DefaultPathObjectComparator();

	@Override
	public int compare(PathObject o1, PathObject o2) {
		
		Objects.nonNull(o1);
		Objects.nonNull(o2);
		
		// Quick check...
		if (o1 == o2)
			return 0;
		
//		// Handle nulls
//		if (o1 == null) {
//			if (o2 == null)
//				return 0;
//			return 1;
//		} else if (o2 == null)
//			return -1;
		
		// Handle class order
		int temp = -Boolean.compare(o1.isRootObject(), o2.isRootObject());
		if (temp != 0)
			return temp;

		temp = -Boolean.compare(o1.isTMACore(), o2.isTMACore());
		if (temp != 0)
			return temp;

		temp = -Boolean.compare(o1.isAnnotation(), o2.isAnnotation());
		if (temp != 0)
			return temp;

		temp = -Boolean.compare(o1.isDetection(), o2.isDetection());
		if (temp != 0)
			return temp;

		// Handle ROI location
		temp = DefaultROIComparator.getInstance().compare(o1.getROI(), o2.getROI());
		if (temp != 0)
			return temp;
		
		// Try object class again
		temp = o1.getClass().getName().compareTo(o2.getClass().getName());
		if (temp != 0)
			return temp;
		
		// Try classifications
		PathClass pc1 = o1.getPathClass();
		PathClass pc2 = o2.getPathClass();
		if (pc1 != null)
			return pc1.compareTo(pc2);
		if (pc2 != null)
			return pc2.compareTo(pc1);
		
		// Shouldn't end up here much...
		return 0;
	}
	
	/**
	 * Get shared comparator instance to sort PathObjects repeatably.
	 * @return
	 */
	public static Comparator<PathObject> getInstance() {
		return instance;
	}
	

}
