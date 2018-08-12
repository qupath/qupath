/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2017 - 2018 the QuPath contributors.
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

import java.util.Collections;
import java.util.List;

import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathLine;
import qupath.lib.roi.interfaces.PathPoints;

/**
 * This class consists exclusively of static methods that operate on or return regions of interest (ROIs).
 * 
 * <p>These methods should be used rather than constructors for individual ROI types.
 * 
 * @author Pete Bankhead
 */
public class ROIs {

	public static PathArea createRectangleROI(double x, double y, double width, double height, int c, int z, int t) {
		return new RectangleROI(x, y, width, height, c, z, t);
	}

	public static PathArea createEllipseROI(double x, double y, double width, double height, int c, int z, int t) {
		return new EllipseROI(x, y, width, height, c, z, t);
	}

	public static PathLine createLineROI(double x, double y, double x2, double y2, int c, int z, int t) {
		return new LineROI(x, y, x2, y2, c, z, t);
	}

	public static PathLine createLineROI(double x, double y, int c, int z, int t) {
		return createLineROI(x, y, x, y, c, z, t);
	}
	
	public static PathPoints createPointsROI(int c, int z, int t) {
		return createPointsROI(Double.NaN, Double.NaN, c, z, t);
	}

	public static PathPoints createPointsROI(double x, double y, int c, int z, int t) {
		return new PointsROI(x, y, c, z, t);
	}
	
	public static PathPoints createPointsROI(List<? extends Point2> points, int c, int z, int t) {
		return new PointsROI(points, c, z, t);
	}

	public static PolygonROI createPolyonROI(List<Point2> points, int c, int z, int t) {
		return new PolygonROI(points, c, z, t);
	}
	
	public static PolygonROI createPolyonROI(double x, double y, int c, int z, int t) {
		return new PolygonROI(Collections.singletonList(new Point2(x, y)), c, z, t);
	}
	
	public static PolylineROI createPolylineROI(List<Point2> points, int c, int z, int t) {
		return new PolylineROI(points, c, z, t);
	}
	
	public static PolylineROI createPolylineROI(double x, double y, int c, int z, int t) {
		return new PolylineROI(Collections.singletonList(new Point2(x, y)), c, z, t);
	}

}
