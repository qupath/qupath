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

package qupath.lib.roi;

import java.util.ArrayList;
import java.util.List;

import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * Several static methods to help when working with ROIs.
 * 
 * @author Pete Bankhead
 *
 */
public class ROIHelpers {
	
	/**
	 * Test if two PathROIs share the same channel, z-slice &amp; time-point
	 * 
	 * @param roi1
	 * @param roi2
	 * @return
	 */
	public static boolean sameImagePlane(ROI roi1, ROI roi2) {
//		if (roi1.getC() != roi2.getC())
//			logger.info("Channels differ");
//		if (roi1.getT() != roi2.getT())
//			logger.info("Timepoints differ");
//		if (roi1.getZ() != roi2.getZ())
//			logger.info("Z-slices differ");
		return roi1.getC() == roi2.getC() && roi1.getT() == roi2.getT() && roi1.getZ() == roi2.getZ();
	}

	/**
	 * Apply a simple 3-point moving average to a list of points.
	 * 
	 * @param points
	 * @return
	 */
	public static List<Point2> smoothPoints(List<Point2> points) {
		List<Point2> points2 = new ArrayList<>(points.size());
		for (int i = 0; i < points.size(); i++) {
			Point2 p1 = points.get((i+points.size()-1)%points.size());
			Point2 p2 = points.get(i);
			Point2 p3 = points.get((i+1)%points.size());
			points2.add(new Point2((p1.getX() + p2.getX() + p3.getX())/3, (p1.getY() + p2.getY() + p3.getY())/3));
		}
		return points2;
	}
	
	
	/**
	 * Returns true if pathROI is an area that contains x &amp; y somewhere within it.
	 * 
	 * @param pathROI
	 * @param x
	 * @param y
	 * @return
	 */
	public static boolean areaContains(final ROI pathROI, final double x, final double y) {
		return (pathROI instanceof PathArea) && ((PathArea)pathROI).contains(x, y);
	}
	
	
	
	
}
