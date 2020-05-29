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

package qupath.lib.roi;

import java.util.Comparator;

import qupath.lib.roi.interfaces.ROI;

/**
 * Default comparator to enable ROIs to be sorted in a more predictable manner.
 * <p>
 * The aim is to help sorted lists to sort first on ROI location (time, z, channel, y coordinate, then x)
 * and afterwards on ROI type.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultROIComparator implements Comparator<ROI>{
	
	private final static DefaultROIComparator instance = new DefaultROIComparator();

	@Override
	public int compare(ROI o1, ROI o2) {
		// Quick check...
		if (o1 == o2)
			return 0;

		// Handle nulls
		if (o1 == null) {
			if (o2 == null)
				return 0;
			return 1;
		} else if (o2 == null)
			return -1;
		
		// Second quick check (just in case equals is overridden)
		if (o1.equals(o2))
			return 0;
		
		// Use time first
		int i1 = o1.getT();
		int i2 = o2.getT();
		int temp = Double.compare(i1, i2);
		if (temp != 0)
			return temp;
		
		// Use Z next
		i1 = o1.getZ();
		i2 = o2.getZ();
		temp = Double.compare(i1, i2);
		if (temp != 0)
			return temp;
		
		// Use channel next
		i1 = o1.getC();
		i2 = o2.getC();
		temp = Double.compare(i1, i2);
		if (temp != 0)
			return temp;
		
		// Use centroids next
		double v1 = o1.getCentroidY();
		double v2 = o2.getCentroidY();
		temp = Double.compare(v1, v2);
		if (temp != 0)
			return temp;
		
		v1 = o1.getCentroidX();
		v2 = o2.getCentroidX();
		temp = Double.compare(v1, v2);
		if (temp != 0)
			return temp;
		
		// Use bounding boxes next
		v1 = o1.getBoundsX();
		v2 = o2.getBoundsX();
		temp = Double.compare(v1, v2);
		if (temp != 0)
			return temp;
		
		v1 = o1.getBoundsY();
		v2 = o2.getBoundsY();
		temp = Double.compare(v1, v2);
		if (temp != 0)
			return temp;
		
		v1 = o1.getBoundsWidth();
		v2 = o2.getBoundsWidth();
		temp = Double.compare(v1, v2);
		if (temp != 0)
			return temp;

		v1 = o1.getBoundsHeight();
		v2 = o2.getBoundsHeight();
		temp = Double.compare(v1, v2);
		if (temp != 0)
			return temp;
		
		// Use type next
		temp = -Boolean.compare(o1 instanceof RectangleROI, o2 instanceof RectangleROI);
		if (temp != 0)
			return temp;
		temp = -Boolean.compare(o1 instanceof EllipseROI, o2 instanceof EllipseROI);
		if (temp != 0)
			return temp;
		temp = -Boolean.compare(o1 instanceof PolygonROI, o2 instanceof PolygonROI);
		if (temp != 0)
			return temp;
		temp = -Boolean.compare(o1 instanceof AreaROI, o2 instanceof AreaROI);
		if (temp != 0)
			return temp;
		temp = -Boolean.compare(o1 instanceof LineROI, o2 instanceof LineROI);
		if (temp != 0)
			return temp;
		temp = -Boolean.compare(o1 instanceof PointsROI, o2 instanceof PointsROI);
		if (temp != 0)
			return temp;
		temp = o1.getClass().getName().compareTo(o2.getClass().getName());
		if (temp != 0)
			return temp;
		
		// Try a few specifics
		if (o1 instanceof LineROI && o2 instanceof LineROI) {
			LineROI r1 = (LineROI)o1;
			LineROI r2 = (LineROI)o2;
			v1 = r1.getY1();
			v2 = r2.getY1();
			temp = Double.compare(v1, v2);
			if (temp != 0)
				return temp;
			v1 = r1.getX1();
			v2 = r2.getX1();
			temp = Double.compare(v1, v2);
			if (temp != 0)
				return temp;
			v1 = r1.getY2();
			v2 = r2.getY2();
			temp = Double.compare(v1, v2);
			if (temp != 0)
				return temp;
			v1 = r1.getX2();
			v2 = r2.getX2();
			return Double.compare(v1, v2);
		}
		
		if (o1 instanceof PointsROI && o2 instanceof PointsROI) {
			PointsROI r1 = (PointsROI)o1;
			PointsROI r2 = (PointsROI)o2;
			int size = Integer.compare(r1.getNumPoints(), r2.getNumPoints());
			if (size != 0)
				return size;
		}
//
		if (o1 instanceof PolygonROI && o2 instanceof PolygonROI) {
			PolygonROI r1 = (PolygonROI)o1;
			PolygonROI r2 = (PolygonROI)o2;
			int size = Integer.compare(r1.nVertices(), r2.nVertices());
			if (size != 0)
				return size;
		}
		
		if (o1 instanceof AreaROI && o2 instanceof AreaROI) {
			AreaROI r1 = (AreaROI)o1;
			AreaROI r2 = (AreaROI)o2;
			int size = Integer.compare(r1.nVertices(), r2.nVertices());
			if (size != 0)
				return size;
		}
		
		var points1 = o1.getAllPoints();
		var points2 = o2.getAllPoints();
		int size = Integer.compare(points1.size(), points2.size());
		if (size != 0)
			return size;
		for (int i = 0; i < points1.size(); i++) {
			var p1 = points1.get(i);
			var p2 = points2.get(i);
			temp = Double.compare(p1.getY(), p2.getY());
			if (temp != 0)
				return temp;
			temp = Double.compare(p1.getX(), p2.getX());
			if (temp != 0)
				return temp;
		}
		
		// Shouldn't happen much... if ever		
		return 0;
	}

	/**
	 * Get a static instance of this comparator, to avoid needing to construct it.
	 * @return
	 */
	public static Comparator<ROI> getInstance() {
		return instance;
	}
	
}
