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

import org.locationtech.jts.geom.Geometry;
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
	 * Create an 'empty' ROI with no length or area on the default image plane.
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
	 * @param x x coordinate of the top left of the rectangle
	 * @param y y coordinate of the top left of the rectangle
	 * @param width width of the rectangle
	 * @param height height of the rectangle
	 * @param plane the image plane where the rectangle should be located
	 * @return a new rectangle ROI
	 */
	public static ROI createRectangleROI(double x, double y, double width, double height, ImagePlane plane) {
		return new RectangleROI(x, y, width, height, plane);
	}

	/**
	 * Create a rectangle ROI defined by its bounding box on the default image plane.
	 *
	 * @param x x coordinate of the top left of the rectangle
	 * @param y y coordinate of the top left of the rectangle
	 * @param width width of the rectangle
	 * @param height height of the rectangle
	 * @return a new rectangle ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createRectangleROI(double x, double y, double width, double height) {
		return createRectangleROI(x, y, width, height, ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create a rectangle ROI that matches an ImageRegion.
	 * @param region an image region defining the rectangle location
	 * @return a new rectangle ROI
	 */
	public static ROI createRectangleROI(ImageRegion region) {
		return new RectangleROI(region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getImagePlane());
	}

	/**
	 * Create an ellipse ROI defined by its bounding box.
	 *
	 * @param x x coordinate of the top left of the ellipse bounding box
	 * @param y y coordinate of the top left of the ellipse bounding box
	 * @param width width of the ellipse bounding box
	 * @param height height of the ellipse bounding box
	 * @param plane the image plane where the rectangle should be located
	 * @return a new ellipse ROI
	 */
	public static ROI createEllipseROI(double x, double y, double width, double height, ImagePlane plane) {
		return new EllipseROI(x, y, width, height, plane);
	}

	/**
	 * Create an ellipse ROI defined by its bounding box on the default image plane.
	 *
	 * @param x x coordinate of the top left of the ellipse bounding box
	 * @param y y coordinate of the top left of the ellipse bounding box
	 * @param width width of the ellipse bounding box
	 * @param height height of the ellipse bounding box
	 * @return a new ellipse ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createEllipseROI(double x, double y, double width, double height) {
		return createEllipseROI(x, y, width, height, ImagePlane.getDefaultPlane());
	}

	/**
	 * Create an ellipse ROI defined by its bounding box.
	 *
	 * @param region an image region defining the ellipse location
	 * @return a new ellipse ROI
	 */
	public static ROI createEllipseROI(ImageRegion region) {
		return createEllipseROI(region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getImagePlane());
	}

	/**
	 * Create a line ROI with start and end coordinates.
	 * 
	 * @param x the start x coordinate
	 * @param y the start y coordinate
	 * @param x2 the end x coordinate
	 * @param y2 the end y coordinate
	 * @param plane the plane containing the ROI
	 * @return a new line ROI
	 */
	public static ROI createLineROI(double x, double y, double x2, double y2, ImagePlane plane) {
		return new LineROI(x, y, x2, y2, plane);
	}

	/**
	 * Create a line ROI with start and end coordinates on the default image plane.
	 *
	 * @param x the start x coordinate
	 * @param y the start y coordinate
	 * @param x2 the end x coordinate
	 * @param y2 the end y coordinate
	 * @return a new line ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createLineROI(double x, double y, double x2, double y2) {
		return createLineROI(x, y, x2, y2, ImagePlane.getDefaultPlane());
	}

	/**
	 * Create a ROI representing a line with zero length.
	 * 
	 * @param x the start and end x coordinate
	 * @param y the start and end y coordinate
	 * @param plane the plane containing the ROI
	 * @return a new line ROI
	 */
	public static ROI createLineROI(double x, double y, ImagePlane plane) {
		return createLineROI(x, y, x, y, plane);
	}
	
	/**
	 * Create an empty points ROI.
	 * @param plane the plane that should contain the ROI
	 * @return a new points ROI
	 */
	public static ROI createPointsROI(ImagePlane plane) {
		return createPointsROI(Double.NaN, Double.NaN, plane);
	}

	/**
	 * Create an empty points ROI on the default image plane.
	 * @return a new points ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createPointsROI() {
		return createPointsROI(ImagePlane.getDefaultPlane());
	}

	/**
	 * Create a points ROI containing a single point.
	 * @param x x coordinate of the point
	 * @param y y coordinate of the point
	 * @param plane the plane that should contain the ROI
	 * @return a new point ROI
	 */
	public static ROI createPointsROI(double x, double y, ImagePlane plane) {
		return new PointsROI(x, y, plane);
	}

	/**
	 * Create a points ROI containing a single point on the default image plane.
	 * @param x x coordinate of the point
	 * @param y y coordinate of the point
	 * @return a new point ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createPointsROI(double x, double y) {
		return createPointsROI(x, y, ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create a points ROI from a list of points.
	 * @param points a list of points to include
	 * @param plane the image plane containing the ROI
	 * @return a new points ROI
	 */
	public static ROI createPointsROI(List<? extends Point2> points, ImagePlane plane) {
		return new PointsROI(points, plane);
	}

	/**
	 * Create a points ROI from a list of points on the default image plane.
	 * @param points a list of points to include
	 * @return a new points ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createPointsROI(List<? extends Point2> points) {
		return createPointsROI(points, ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create a points ROI from an array of x and y coordinates.
	 * @param x x coordinates for the points
	 * @param y y coordinates for the points
	 * @param plane the image plane to contain the ROI
	 * @return a new points ROI
	 * @throws IllegalArgumentException if x and y have a different length
	 */
	public static ROI createPointsROI(double[] x, double[] y, ImagePlane plane) throws IllegalArgumentException {
		if (x.length != y.length)
			throw new IllegalArgumentException("Point arrays have different lengths!");
		var points = new ArrayList<Point2>();
		for (int i = 0; i < x.length; i++)
			points.add(new Point2(x[i], y[i]));
		return new PointsROI(points, plane);
	}

	/**
	 * Create a points ROI from an array of x and y coordinates on the default image plane.
	 * @param x x coordinates for the points
	 * @param y y coordinates for the points
	 * @return a new points ROI
	 * @throws IllegalArgumentException if x and y have a different length
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createPointsROI(double[] x, double[] y) throws IllegalArgumentException {
		return createPointsROI(x, y, ImagePlane.getDefaultPlane());
	}

	/**
	 * Create a closed polygon ROI from a list of points.
	 * @param points the vertices of the polygon
	 * @param plane the plane containing the ROI
	 * @return a new polygon ROI
	 */
	public static PolygonROI createPolygonROI(List<? extends Point2> points, ImagePlane plane) {
		return new PolygonROI(points, plane);
	}

	/**
	 * Create a closed polygon ROI from a list of points on the default image plane.
	 * @param points the vertices of the polygon
	 * @return a new polygon ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static PolygonROI createPolygonROI(List<? extends Point2> points) {
		return createPolygonROI(points, ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create a polygon ROI from an array of x and y coordinates.
	 * @param x x coordinates of the polygon vertices
	 * @param y y coordinates of the polygon vertices
	 * @param plane the plane containing the ROI
	 * @return a new polygon ROI
	 * @throws IllegalArgumentException if x and y have a different length
	 */
	public static ROI createPolygonROI(double[] x, double[] y, ImagePlane plane) throws IllegalArgumentException {
		if (x.length != y.length)
			throw new IllegalArgumentException("Arrays have different lengths!");
		var points = new ArrayList<Point2>();
		for (int i = 0; i < x.length; i++)
			points.add(new Point2(x[i], y[i]));
		return new PolygonROI(points, plane);
	}

	/**
	 * Create a polygon ROI from an array of x and y coordinates on the default image plane
	 * @param x x coordinates of the polygon vertices
	 * @param y y coordinates of the polygon vertices
	 * @return a new polygon ROI
	 * @throws IllegalArgumentException if x and y have a different length
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createPolygonROI(double[] x, double[] y) throws IllegalArgumentException {
		return createPolygonROI(x, y, ImagePlane.getDefaultPlane());
	}

	
	/**
	 * Create an empty, closed polygon ROI consisting of a single point.
	 * 
	 * @param x x coordinate for the only polygon point
	 * @param y y coordinate for the only polygon point
	 * @param plane the image plane containing the ROI
	 * @return a new polygon ROI
	 */
	public static PolygonROI createPolygonROI(double x, double y, ImagePlane plane) {
		return new PolygonROI(Collections.singletonList(new Point2(x, y)), plane);
	}

	/**
	 * Create an empty, closed polygon ROI consisting of a single point on the default image plane.
	 *
	 * @param x x coordinate for the only polygon point
	 * @param y y coordinate for the only polygon point
	 * @return a new polygon ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static PolygonROI createPolygonROI(double x, double y) {
		return createPolygonROI(x, y, ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create a polyline ROI from a list of points.
	 * @param points the vertices of the polyline
	 * @param plane the plane containing the ROI
	 * @return a new polyline ROI
	 */
	public static PolylineROI createPolylineROI(List<? extends Point2> points, ImagePlane plane) {
		return new PolylineROI(points, plane);
	}

	/**
	 * Create a polyline ROI from a list of points on the default image plane.
	 * @param points the vertices of the polyline
	 * @return a new polyline ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static PolylineROI createPolylineROI(List<? extends Point2> points) {
		return createPolylineROI(points, ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create an empty polyline ROI consisting of a single point.
	 * @param x x coordinate of the point
	 * @param y y coordinate of the point
	 * @param plane the image plane containing the ROI
	 * @return a new polyline ROI
	 */
	public static PolylineROI createPolylineROI(double x, double y, ImagePlane plane) {
		return createPolylineROI(Collections.singletonList(new Point2(x, y)), plane);
	}

	/**
	 * Create an empty polyline ROI consisting of a single point on the default iamge plane.
	 * @param x x coordinate of the point
	 * @param y y coordinate of the point
	 * @return a new polyline ROI
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static PolylineROI createPolylineROI(double x, double y) {
		return createPolylineROI(x, y, ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create a polyline ROI from an array of x and y coordinates.
	 * @param x x coordinates of the polyline vertices
	 * @param y y coordinates of the polyline vertices
	 * @return a new polygon ROI
	 * @throws IllegalArgumentException if x and y have a different length
	 */
	public static ROI createPolylineROI(double[] x, double[] y, ImagePlane plane) throws IllegalArgumentException {
		if (x.length != y.length)
			throw new IllegalArgumentException("Arrays have different lengths!");
		var points = new ArrayList<Point2>();
		for (int i = 0; i < x.length; i++)
			points.add(new Point2(x[i], y[i]));
		return new PolylineROI(points, plane);
	}

	/**
	 * Create a polyline ROI from an array of x and y coordinates on the default image plane.
	 * @param x x coordinates of the polyline vertices
	 * @param y y coordinates of the polyline vertices
	 * @return a new polygon ROI
	 * @throws IllegalArgumentException if x and y have a different length
	 * @see ImagePlane#getDefaultPlane()
	 * @since v0.6.0
	 */
	public static ROI createPolylineROI(double[] x, double[] y) throws IllegalArgumentException {
		return createPolylineROI(x, y, ImagePlane.getDefaultPlane());
	}
	
	/**
	 * Create an area ROI representing a 2D shape.
	 * <p>
	 * The resulting ROI may consist of multiple disconnected regions, possibly containing holes.
	 * 
	 * @param shape the shape for the ROI
	 * @param plane the plane that should contain the ROI
	 * @return a new ROI representing the shape
	 * @deprecated v0.6.0, use {@link RoiTools#getShapeROI(Shape, ImagePlane, double)} instead.
	 *             This method does not necessarily give the expected results for shapes that do not represent an area.
	 */
	@Deprecated
	public static ROI createAreaROI(Shape shape, ImagePlane plane) {
		return new GeometryROI(GeometryTools.shapeToGeometry(shape), plane);
	}

}
