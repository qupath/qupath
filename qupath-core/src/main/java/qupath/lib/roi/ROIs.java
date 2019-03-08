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

import java.awt.Shape;
import java.util.Collections;
import java.util.List;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathLine;
import qupath.lib.roi.interfaces.PathPoints;
import qupath.lib.roi.interfaces.ROI;

/**
 * This class consists exclusively of static methods that operate on or return regions of interest (ROIs).
 * 
 * <p>These methods should be used rather than constructors for individual ROI types.
 * 
 * @author Pete Bankhead
 */
public class ROIs {

	/**
	 * Create an 'empty' ROI with no length or area.
	 * 
	 * <p>The only guarantee is that it will return {@code isEmpty() == true}
	 * 
	 * @return
	 */
	public static ROI createEmptyROI() {
		return createRectangleROI(0, 0, 0, 0, ImagePlane.getDefaultPlane());
	}
	
	
	public static PathArea createRectangleROI(double x, double y, double width, double height, ImagePlane plane) {
		return new RectangleROI(x, y, width, height, plane.getC(), plane.getZ(), plane.getT());
	}

	public static PathArea createEllipseROI(double x, double y, double width, double height, ImagePlane plane) {
		return new EllipseROI(x, y, width, height, plane.getC(), plane.getZ(), plane.getT());
	}

	public static PathLine createLineROI(double x, double y, double x2, double y2, ImagePlane plane) {
		return new LineROI(x, y, x2, y2, plane.getC(), plane.getZ(), plane.getT());
	}

	public static PathLine createLineROI(double x, double y, ImagePlane plane) {
		return createLineROI(x, y, x, y, plane);
	}
	
	public static PathPoints createPointsROI(ImagePlane plane) {
		return createPointsROI(Double.NaN, Double.NaN, plane);
	}

	public static PathPoints createPointsROI(double x, double y, ImagePlane plane) {
		return new PointsROI(x, y, plane.getC(), plane.getZ(), plane.getT());
	}
	
	public static PathPoints createPointsROI(List<? extends Point2> points, ImagePlane plane) {
		return new PointsROI(points, plane.getC(), plane.getZ(), plane.getT());
	}

	public static PolygonROI createPolygonROI(List<Point2> points, ImagePlane plane) {
		return new PolygonROI(points, plane.getC(), plane.getZ(), plane.getT());
	}
	
	public static PolygonROI createPolygonROI(double x, double y, ImagePlane plane) {
		return new PolygonROI(Collections.singletonList(new Point2(x, y)), plane.getC(), plane.getZ(), plane.getT());
	}
	
	public static PolylineROI createPolylineROI(List<Point2> points, ImagePlane plane) {
		return new PolylineROI(points, plane.getC(), plane.getZ(), plane.getT());
	}
	
	public static PolylineROI createPolylineROI(double x, double y, ImagePlane plane) {
		return new PolylineROI(Collections.singletonList(new Point2(x, y)), plane.getC(), plane.getZ(), plane.getT());
	}
	
	public static PathArea createAreaROI(Shape shape, ImagePlane plane) {
		return new AWTAreaROI(shape, plane.getC(), plane.getZ(), plane.getT());
	}

}
