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

import java.util.List;

import qupath.lib.geom.Point2;

/**
 * Class to help ascertain when a point falls inside a particular shape.
 * <p>
 * Implementation is based on the C++ code by Dan Sunday at http://geomalgorithms.com/a03-_inclusion.html
 * 
 * @author Pete Bankhead
 *
 */
class WindingTest {
	
	/*
	 * Note the following copyright notice for the original C++ implementation of the code:
	 */
	// Copyright 2000 softSurfer, 2012 Dan Sunday
	// This code may be freely used and modified for any purpose
	// providing that this copyright notice is included with it.
	// SoftSurfer makes no warranty for this code, and cannot be held
	// liable for any real or imagined damage resulting from its use.
	// Users of this code must verify correctness for their application.


	public static int isLeft(
			final double x1, final double y1,
			final double x2, final double y2,
			final double x3, final double y3) {
		return (int)Math.signum( (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1) );
	}


	public static int getWindingNumber(final List<Point2> points, final double x, final double y) {
		if (points.size() <= 2)
			return 0;
		int nPoints = points.size();
		int wn = 0;
		for (int i = 0; i < nPoints; i++) {
			Point2 p = points.get(i);
			Point2 p2 = points.get((i + 1) % nPoints);
			if (p.getY() <= y) {          // start y <= P.y
				if (p2.getY()  > y)      // an upward crossing
					if (isLeft(p.getX(), p.getY(), p2.getX(), p2.getY(), x, y) > 0)  // P left of  edge
						wn++;            // have  a valid up intersect
			}
			else {                        // start y > P.y (no test needed)
				if (p2.getY()  <= y)     // a downward crossing
					if (isLeft(p.getX(), p.getY(), p2.getX(), p2.getY(), x, y) < 0)  // P right of  edge
						wn--;            // have  a valid down intersect
			}
		}
		return wn;
	}
	
	
	
	
	public static int getWindingNumber(final Vertices vertices, final double x, final double y) {
		if (vertices.size() <= 2)
			return 0;
		int nPoints = vertices.size();
		int wn = 0;
		for (int i = 0; i < nPoints; i++) {
			Point2 p = vertices.get(i);
			Point2 p2 = vertices.get((i + 1) % nPoints);
			if (p.getY() <= y) {          // start y <= P.y
				if (p2.getY()  > y)      // an upward crossing
					if (isLeft(p.getX(), p.getY(), p2.getX(), p2.getY(), x, y) > 0)  // P left of  edge
						wn++;            // have  a valid up intersect
			}
			else {                        // start y > P.y (no test needed)
				if (p2.getY()  <= y)     // a downward crossing
					if (isLeft(p.getX(), p.getY(), p2.getX(), p2.getY(), x, y) < 0)  // P right of  edge
						wn--;            // have  a valid down intersect
			}
		}
		return wn;
	}
	
	

}