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

/**
 * Base interface for defining regions of interest (ROIs) within QuPath.
 * 
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
	 * (This is not currently used, but may be in the future)
	 * @return
	 */
	public abstract int getC();

	/**
	 * Get time point index.
	 * Default is 0 if the image it relates to is not a time series.
	 * @return
	 */
	public abstract int getT();

	/**
	 * Get z-stack slice index.
	 * Default is 0 if the image it relates to is not a z-stack.
	 * @return
	 */
	public abstract int getZ();

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
	
	public List<Point2> getPolygonPoints();

	/**
	 * Returns true if the ROI bounds have zero width and height.
	 * 
	 * @return
	 */
	public abstract boolean isEmpty();

	/**
	 * Create a duplicate of the ROI.
	 * 
	 * This method is deprecated, since ROIs are (or are moving towards being) immutable... making it pointless to duplicate them.
	 * 
	 * @return
	 */
	@Deprecated
	public abstract ROI duplicate();
	
	/**
	 * Returns a java.awt.Shape representing this ROI, if possible.
	 * 
	 * <p>Note that PointROI throws an UnsupportedOperationException as it cannot 
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
	

}