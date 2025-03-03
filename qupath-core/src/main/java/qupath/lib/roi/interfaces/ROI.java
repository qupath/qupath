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

package qupath.lib.roi.interfaces;

import java.awt.Shape;
import java.util.List;

import org.locationtech.jts.geom.Geometry;

import qupath.lib.geom.Point2;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;

/**
 * Base interface for defining regions of interest (ROIs) within QuPath.
 * <p>
 * In general, anything that returns a coordinate should be defined in terms of pixels 
 * from the top left of the full-resolution image.
 * 
 * @author Pete Bankhead
 *
 */
public interface ROI {

	/**
	 * Get a String representation of the kind of ROI we have, 
	 * e.g. "Rectangle", "Ellipse", "Polygon"
	 * 
	 * @return
	 */
	String getRoiName();

	/**
	 * Get channel index, or -1 if the ROI relates to all available channels.
	 * <p>
	 * (This is not currently used, but may be in the future)
	 * @return
	 */
	int getC();

	/**
	 * Get time point index.
	 * <p>
	 * Default is 0 if the image it relates to is not a time series.
	 * @return
	 */
	int getT();

	/**
	 * Get z-stack slice index.
	 * <p>
	 * Default is 0 if the image it relates to is not a z-stack.
	 * @return
	 */
	int getZ();

	/**
	 * Get the ImagePlane, which contains the values for c, z and t in a single object.
	 * @return 
	 */
	ImagePlane getImagePlane();
	
	/**
	 * Returns the x coordinate for the ROI centroid.
	 * 
	 * @return
	 */
	double getCentroidX();

	/**
	 * Returns the y coordinate for the ROI centroid.
	 * 
	 * @return
	 */
	double getCentroidY();

	/**
	 * Returns the x coordinate for the top left of the ROI bounding box.
	 * 
	 * @return
	 */
	double getBoundsX();

	/**
	 * Returns the y coordinate for the top left of the ROI bounding box.
	 * 
	 * @return
	 */
	double getBoundsY();

	/**
	 * Returns the width of the ROI bounding box.
	 * 
	 * @return
	 */
	double getBoundsWidth();

	/**
	 * Returns the height of the ROI bounding box.
	 * 
	 * @return
	 */
	double getBoundsHeight();
	
	/**
	 * Get a list of points representing the vertices of the ROI.
	 * <p>
	 * This is only really well-defined for ROIs where a single set of vertices represents the shape completely; 
	 * the expected output for a ROI that contains holes or disconnected regions is (currently) undefined.
	 * @return
	 */
	List<Point2> getAllPoints();

	/**
	 * Get the number of points, as would be returned by {@link #getAllPoints()}.
	 * @return
	 */
	int getNumPoints();
		
	/**
	 * Returns true if the ROI bounds have zero width and height.
	 * 
	 * @return
	 */
	 boolean isEmpty();

	/**
	 * Create a duplicate of the ROI.
	 * <p>
	 * This method is deprecated, since ROIs are (or are moving towards being) immutable... making it pointless to duplicate them.
	 * 
	 * @return
	 */
	@Deprecated
    ROI duplicate();
	
	/**
	 * Returns a java.awt.Shape representing this ROI, if possible.
	 * <p>
	 * Note that PointROI throws an UnsupportedOperationException as it cannot 
	 * adequately be represented by a Shape object.
	 * 
	 * @return
	 */
    Shape getShape();

	
	/**
	 * Returns a org.locationtech.jts.geom.Geometry object.
	 * 
	 * @return
	 */
    Geometry getGeometry();
	
	/**
	 * Enum representing the major different types of ROI.
	 */
    enum RoiType {
		/**
		 * ROI represents a closed area (possibly with holes).
		 */
		AREA,
		/**
		 * ROI represents a line or polyline.
		 */	
		LINE,
		/**
		 * ROI represents points.
		 */
		POINT
	}
	
	/**
	 * Get the RoiType, used to distinguish between points, lines and areas.
	 * @return
	 */
    RoiType getRoiType();
	
	/**
	 * Returns true if this ROI consists of line segments and does not enclose an area.
	 * 
	 * @return
	 */
    boolean isLine();
	
	/**
	 * Returns true if this ROI encloses an area.
	 * 
	 * @return
	 */
    boolean isArea();
	
	/**
	 * Returns true if this ROI represents distinct (unconnected) points.
	 * 
	 * @return
	 */
    boolean isPoint();
	
	/**
	 * Create a translated version of this ROI. The original ROI is unchanged.
	 * @param dx horizontal translation
	 * @param dy vertical translation
	 * @return
	 */
    ROI translate(double dx, double dy);
	
	/**
	 * Create a scaled version of this ROI. Coordinates are multiplied by the given 
	 * scaling factors, while the original ROI is unchanged.
	 * @param scaleX horizontal scale value
	 * @param scaleY vertical scale value
	 * @param originX value subtracted from each x-ordinate prior to scaling, and added back afterwards
	 * @param originY value subtracted from each y-ordinate prior to scaling, and added back afterwards
	 * @return
	 * @see #scale(double, double)
	 */
    ROI scale(double scaleX, double scaleY, double originX, double originY);
	
	/**
	 * Create a scaled version of this ROI. Coordinates are multiplied by the given 
	 * scaling factors, while the original ROI is unchanged. The scaling uses 0,0 as the origin.
	 * @param scaleX horizontal scale value
	 * @param scaleY vertical scale value
	 * @return
	 * @see #scale(double, double, double, double)
	 */
	default ROI scale(double scaleX, double scaleY) {
		return scale(scaleX, scaleY, 0, 0);
	}
	
	/**
	 * Get the area of this ROI. For lines and points this returns 0.
	 * @return
	 * @see #getScaledArea(double, double)
	 */
    double getArea();
	
	/**
	 * Get scaled area of the ROI, for use with calibrated pixel sizes.
	 * @param pixelWidth
	 * @param pixelHeight
	 * @return
	 * @see #getArea()
	 */
    double getScaledArea(double pixelWidth, double pixelHeight);

	/**
	 * Get the scaled area, using the pixel width and height from a calibration object.
	 * Note that the units are not defined; it is a convenience method equivalent to
	 * <code>
	 *     getScaledArea(cal.getPixelWidth().doubleValue(), cal.getPixelHeight().doubleValue())
	 * </code>
	 * For more control, use {@link #getScaledArea(double, double)}
	 * @param cal
	 * @return
	 */
	default double getScaledArea(PixelCalibration cal) {
		return getScaledArea(cal.getPixelWidth().doubleValue(), cal.getPixelHeight().doubleValue());
	}

	/**
	 * Get ROI length.
	 * This is defined as
	 * <ul>
	 *   <li><i>perimeter</i> in the case of area ROIs</li>
	 *   <li><i>total length of line segments</i> in the case of line or polyline ROIs</li>
	 *   <li>0 in the case of point ROIs</li>
	 * </ul>
	 * @return
	 * @see #getScaledLength(double, double)
	 */
	double getLength();
	
	/**
	 * Get the scaled length, for use with calibrated pixel sizes.
	 * @param pixelWidth
	 * @param pixelHeight
	 * @return
	 * @see #getLength()
	 */
	double getScaledLength(double pixelWidth, double pixelHeight);

	/**
	 * Get the scaled length, using the pixel width and height from a calibration object.
	 * Note that the units are not defined; it is a convenience method equivalent to
	 * <code>
	 *     getScaledLength(cal.getPixelWidth().doubleValue(), cal.getPixelHeight().doubleValue())
	 * </code>
	 * For more control, use {@link #getScaledLength(double, double)}
	 * @param cal
	 * @return
	 */
	default double getScaledLength(PixelCalibration cal) {
		return getScaledLength(cal.getPixelWidth().doubleValue(), cal.getPixelHeight().doubleValue());
	}
	
	/**
	 * Get a ROI representing the convex hull of this ROI.
	 * This should be the smallest convex shape that contains all the ROI points.
	 * @return
	 */
	ROI getConvexHull();
	
	/**
	 * Calculate the solidity, defined as ROI area / convex hull area.
	 * Returns Double.NaN if the ROI does not represent an area.
	 * @return
	 */
	double getSolidity();
	
	/**
	 * Test is the ROI contains specified x, y coordinates.
	 * Only Area ROIs can return true, i.e. where {@link #isArea()} returns true. 
	 * All other ROIs (points, lines) return false.
	 * @param x
	 * @param y
	 * @return
	 */
	boolean contains(double x, double y);

	/**
	 * Test if the ROI intersects a specified image region.
	 * <p>
	 * Note that this test is intended as a fast initial filter; a more detailed test using
	 * {@link #getGeometry()} is recommended when exact results are needed.
	 *
	 * @param region the region to test
	 * @return true if the ROI intersects the region, false otherwise
	 */
	default boolean intersects(ImageRegion region) {
		return getZ() == region.getZ() && getT() == region.getT() &&
				intersects(region.getX(), region.getY(), region.getWidth(), region.getHeight());
	}

	/**
	 * Test if the ROI intersects a specified region.
	 * <p>
	 * Note that this test is intended as a fast initial filter; a more detailed test using
	 * {@link #getGeometry()} is recommended when exact results are needed.
	 *
	 * @param x the x coordinate of the region bounding box
	 * @param y the y coordinate of the region bounding box
	 * @param width the width of the region bounding box
	 * @param height the height of the region bounding box
	 * @return true if the ROI intersects the region, false otherwise
	 */
	boolean intersects(double x, double y, double width, double height);

	/**
	 * Create a new ROI defining the same region on a different {@link ImagePlane}.
	 * The original ROI is unchanged.
	 * @param plane the new plane to use
	 * @return
	 */
	ROI updatePlane(ImagePlane plane);
	

}