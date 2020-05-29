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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;

/**
 * Helper method for calculating the convex hull from a list of points.
 * 
 * @author Pete Bankhead
 *
 */
public class ConvexHull {
	
	final private static Logger logger = LoggerFactory.getLogger(ConvexHull.class);

	
	/**
	 * TODO: Consider a more efficient convex hull calculation.
	 * 
	 * For implementation details, see
	 * <ul>
	 * 	<li>http://en.wikipedia.org/wiki/Gift_wrapping_algorithm</li>
	 * 	<li>http://en.wikipedia.org/wiki/Graham_scan</li>
	 * </ul>
	 * 
	 * @param points 
	 * @return
	 */
	public static List<Point2> getConvexHull(List<Point2> points) {
		if (points == null || points.isEmpty())
			return null;
		
		// Find the left-most point
		Point2 pointOnHull = points.get(0);
		for (Point2 p : points) {
			if (p.getX() < pointOnHull.getX())
				pointOnHull = p;
		}
		
		List<Point2> hull = new ArrayList<>(points.size());
		while (true) {
//			logger.info("Adding: " + pointOnHull);
			hull.add(pointOnHull);
			Point2 endPoint = points.get(0);
			double dx = endPoint.getX() - pointOnHull.getX();
			double dy = endPoint.getY() - pointOnHull.getY();
			for (Point2 s : points) {
				double sx = s.getX() - pointOnHull.getX();
				double sy = s.getY() - pointOnHull.getY();
				if ((endPoint.equals(pointOnHull)) || sx*dy - sy*dx > 0) {
					endPoint = s;
					dx = sx;
					dy = sy;
				}
			}
			pointOnHull = endPoint;
			if (endPoint.equals(hull.get(0))) {
				logger.trace("Original points: {}, Convex hull points: {}", points.size(), hull.size());
				return hull;
			}
//			if (endPoint == hull.get(0) || endPoint.distanceSq(hull.get(0)) < 0.00000001)
//				return hull;
		}
	}
	
}
