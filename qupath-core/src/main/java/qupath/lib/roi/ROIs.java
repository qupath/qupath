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

import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * This class consists exclusively of static methods that operate on or return regions of interest (ROIs).
 * <p>
 * These methods should be used rather than constructors for individual ROI types.
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
		return createEmptyROI(ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create an 'empty' ROI with no length or area.
	 * 
	 * <p>The only guarantee is that it will return {@code isEmpty() == true}
	 * 
	 * @param plane the plane associated with the ROI
	 * @return
	 */
	static ROI createEmptyROI(ImagePlane plane) {
		return createRectangleROI(0, 0, 0, 0, plane);
	}
	
	/**
	 * Create a rectangle ROI defined by its bounding box.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param plane
	 * @return
	 */
	public static ROI createRectangleROI(double x, double y, double width, double height, ImagePlane plane) {
		return new RectangleROI(x, y, width, height, plane);
	}
	
	/**
	 * Create a rectangle ROI that matches an ImageRegion.
	 * @param region
	 * @return
	 */
	public static ROI createRectangleROI(ImageRegion region) {
		return new RectangleROI(region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getImagePlane());
	}

	/**
	 * Create an ellipse ROI defined by its bounding box.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param plane
	 * @return
	 */
	public static ROI createEllipseROI(double x, double y, double width, double height, ImagePlane plane) {
		return new EllipseROI(x, y, width, height, plane);
	}

	/**
	 * Create a line ROI with start and end coordinates.
	 * 
	 * @param x
	 * @param y
	 * @param x2
	 * @param y2
	 * @param plane
	 * @return
	 */
	public static ROI createLineROI(double x, double y, double x2, double y2, ImagePlane plane) {
		return new LineROI(x, y, x2, y2, plane);
	}

	/**
	 * Create a ROI representing a line with zero length.
	 * 
	 * @param x
	 * @param y
	 * @param plane
	 * @return
	 */
	public static ROI createLineROI(double x, double y, ImagePlane plane) {
		return createLineROI(x, y, x, y, plane);
	}
	
	/**
	 * Create an empty points ROI.
	 * 
	 * @param plane
	 * @return
	 */
	public static ROI createPointsROI(ImagePlane plane) {
		return createPointsROI(Double.NaN, Double.NaN, plane);
	}

	/**
	 * Create a points ROI containing a single point.
	 * @param x
	 * @param y
	 * @param plane
	 * @return
	 */
	public static ROI createPointsROI(double x, double y, ImagePlane plane) {
		return new PointsROI(x, y, plane);
	}
	
	/**
	 * Create a points ROI from a list of points.
	 * @param points
	 * @param plane
	 * @return
	 */
	public static ROI createPointsROI(List<? extends Point2> points, ImagePlane plane) {
		return new PointsROI(points, plane);
	}
	
	/**
	 * Create a points ROI from an array of x and y coordinates.
	 * @param x
	 * @param y
	 * @param plane
	 * @return
	 */
	public static ROI createPointsROI(double[] x, double y[], ImagePlane plane) {
		if (x.length != y.length)
			throw new IllegalArgumentException("Point arrays have different lengths!");
		var points = new ArrayList<Point2>();
		for (int i = 0; i < x.length; i++)
			points.add(new Point2(x[i], y[i]));
		return new PointsROI(points, plane);
	}

	/**
	 * Create a closed polygon ROI from a list of points.
	 * @param points
	 * @param plane
	 * @return
	 */
	public static PolygonROI createPolygonROI(List<Point2> points, ImagePlane plane) {
		return new PolygonROI(points, plane);
	}
	
	/**
	 * Create a polygon ROI from an array of x and y coordinates.
	 * @param x
	 * @param y
	 * @param plane
	 * @return
	 */
	public static ROI createPolygonROI(double[] x, double y[], ImagePlane plane) {
		if (x.length != y.length)
			throw new IllegalArgumentException("Arrays have different lengths!");
		var points = new ArrayList<Point2>();
		for (int i = 0; i < x.length; i++)
			points.add(new Point2(x[i], y[i]));
		return new PolygonROI(points, plane);
	}

	
	/**
	 * Create an empty, closed polygon ROI consisting of a single point.
	 * 
	 * @param x
	 * @param y
	 * @param plane
	 * @return
	 */
	public static PolygonROI createPolygonROI(double x, double y, ImagePlane plane) {
		return new PolygonROI(Collections.singletonList(new Point2(x, y)), plane);
	}
	
	/**
	 * Create a polyline ROI from a list of points.
	 * @param points
	 * @param plane
	 * @return
	 */
	public static PolylineROI createPolylineROI(List<Point2> points, ImagePlane plane) {
		return new PolylineROI(points, plane);
	}
	
	/**
	 * Create an empty polyline ROI consisting of a single point.
	 * @param x
	 * @param y
	 * @param plane
	 * @return
	 */
	public static PolylineROI createPolylineROI(double x, double y, ImagePlane plane) {
		return new PolylineROI(Collections.singletonList(new Point2(x, y)), plane);
	}
	
	/**
	 * Create a polygon ROI from an array of x and y coordinates.
	 * @param x
	 * @param y
	 * @param plane
	 * @return
	 */
	public static ROI createPolylineROI(double[] x, double y[], ImagePlane plane) {
		if (x.length != y.length)
			throw new IllegalArgumentException("Arrays have different lengths!");
		var points = new ArrayList<Point2>();
		for (int i = 0; i < x.length; i++)
			points.add(new Point2(x[i], y[i]));
		return new PolylineROI(points, plane);
	}
	
	/**
	 * Create an area ROI representing a 2D shape.
	 * <p>
	 * The resulting ROI may consist of multiple disconnected regions, possibly containing holes.
	 * 
	 * @param shape
	 * @param plane
	 * @return
	 */
	public static ROI createAreaROI(Shape shape, ImagePlane plane) {
		return new GeometryROI(GeometryTools.shapeToGeometry(shape), plane);
//		return new AWTAreaROI(shape, plane);
	}

}
