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

import java.util.List;

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

	public abstract String getROIType();

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

	// Centroid functions
	public abstract double getCentroidX();

	public abstract double getCentroidY();

	// Bounding box
	public abstract double getBoundsX();

	public abstract double getBoundsY();

	public abstract double getBoundsWidth();

	public abstract double getBoundsHeight();
	
	public List<Point2> getPolygonPoints();

	/**
	 * A ROI is 'empty' if its bounds have no width or height.
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

}