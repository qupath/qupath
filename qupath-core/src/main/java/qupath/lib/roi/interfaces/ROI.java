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

package qupath.lib.roi.interfaces;

import java.awt.Shape;
import java.util.List;

import org.locationtech.jts.geom.Geometry;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;

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
	public abstract String getRoiName();

	/**
	 * Get channel index, or -1 if the ROI relates to all available channels.
	 * <p>
	 * (This is not currently used, but may be in the future)
	 * @return
	 */
	public abstract int getC();

	/**
	 * Get time point index.
	 * <p>
	 * Default is 0 if the image it relates to is not a time series.
	 * @return
	 */
	public abstract int getT();

	/**
	 * Get z-stack slice index.
	 * <p>
	 * Default is 0 if the image it relates to is not a z-stack.
	 * @return
	 */
	public abstract int getZ();

	/**
	 * Get the ImagePlane, which contains the values for c, z and t in a single object.
	 */
	public ImagePlane getImagePlane();
	
	/**
	 * Returns the x coordinate for the ROI centroid.
	 * 
	 * @return
	 */
	public abstract double getCentroidX();

	/**
	 * Returns the y coordinate for the ROI centroid.
	 * 
	 * @return
	 */
	public abstract double getCentroidY();

	/**
	 * Returns the x coordinate for the top left of the ROI bounding box.
	 * 
	 * @return
	 */
	public abstract double getBoundsX();

	/**
	 * Returns the y coordinate for the top left of the ROI bounding box.
	 * 
	 * @return
	 */
	public abstract double getBoundsY();

	/**
	 * Returns the width of the ROI bounding box.
	 * 
	 * @return
	 */
	public abstract double getBoundsWidth();

	/**
	 * Returns the height of the ROI bounding box.
	 * 
	 * @return
	 */
	public abstract double getBoundsHeight();
	
	/**
	 * Get a list of points representing the vertices of the ROI.
	 * <p>
	 * This is only really well-defined for ROIs where a single set of vertices represents the shape completely; 
	 * the expected output for a ROI that contains holes or disconnected regions is (currently) undefined.
	 * @return
	 */
	public abstract List<Point2> getAllPoints();

	/**
	 * Get the number of points, as would be returned by {@link #getAllPoints()}.
	 * @return
	 */
	public abstract int getNumPoints();
		
	/**
	 * Returns true if the ROI bounds have zero width and height.
	 * 
	 * @return
	 */
	public abstract boolean isEmpty();

	/**
	 * Create a duplicate of the ROI.
	 * <p>
	 * This method is deprecated, since ROIs are (or are moving towards being) immutable... making it pointless to duplicate them.
	 * 
	 * @return
	 */
	@Deprecated
	public abstract ROI duplicate();
	
	/**
	 * Returns a java.awt.Shape representing this ROI, if possible.
	 * <p>
	 * Note that PointROI throws an UnsupportedOperationException as it cannot 
	 * adequately be represented by a Shape object.
	 * 
	 * @return
	 */
	public Shape getShape();

	
	/**
	 * Returns a org.locationtech.jts.geom.Geometry object.
	 * 
	 * @return
	 */
	public Geometry getGeometry();
	
	/**
	 * Enum representing the major different types of ROI.
	 */
	public enum RoiType {
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
	public RoiType getRoiType();
	
	/**
	 * Returns true if this ROI consists of line segments and does not enclose an area.
	 * 
	 * @return
	 */
	public boolean isLine();
	
	/**
	 * Returns true if this ROI encloses an area.
	 * 
	 * @return
	 */
	public boolean isArea();
	
	/**
	 * Returns true if this ROI represents distinct (unconnected) points.
	 * 
	 * @return
	 */
	public boolean isPoint();
	
	/**
	 * Create a translated version of this ROI. The original ROI is unchanged.
	 * @param dx horizontal translation
	 * @param dy vertical translation
	 * @return
	 */
	public ROI translate(double dx, double dy);
	
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
	public ROI scale(double scaleX, double scaleY, double originX, double originY);
	
	/**
	 * Create a scaled version of this ROI. Coordinates are multiplied by the given 
	 * scaling factors, while the original ROI is unchanged. The scaling uses 0,0 as the origin.
	 * @param scaleX horizontal scale value
	 * @param scaleY vertical scale value
	 * @return
	 * @see #scale(double, double, double, double)
	 */
	public default ROI scale(double scaleX, double scaleY) {
		return scale(scaleX, scaleY, 0, 0);
	}
	
	/**
	 * Get the area of this ROI. For lines and points this returns 0.
	 * @return
	 * @see #getScaledArea(double, double)
	 */
	public double getArea();
	
	/**
	 * Get scaled area of the ROI, for use with calibrated pixel sizes.
	 * @param pixelWidth
	 * @param pixelHeight
	 * @return
	 * @see #getArea()
	 */
	public double getScaledArea(double pixelWidth, double pixelHeight);
	
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
	public double getLength();
	
	/**
	 * Get the scaled length, for use with calibrated pixel sizes.
	 * @param pixelWidth
	 * @param pixelHeight
	 * @return
	 * @see #getLength()
	 */
	public double getScaledLength(double pixelWidth, double pixelHeight);
	
	
	/**
	 * Get a ROI representing the convex hull of this ROI.
	 * This should be the smallest convex shape that contains all the ROI points.
	 * @return
	 */
	public ROI getConvexHull();
	
	/**
	 * Calculate the solidity, defined as ROI area / convex hull area.
	 * Returns Double.NaN if the ROI does not represent an area.
	 * @return
	 */
	public double getSolidity();
	
	/**
	 * Test is the ROI contains specified x, y coordinates.
	 * Only Area ROIs can return true, i.e. where {@link #isArea()} returns true. 
	 * All other ROIs (points, lines) return false.
	 * @param x
	 * @param y
	 * @return
	 */
	public boolean contains(double x, double y);
	

}