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

/**
 * Interface to define ROIs for which a convex hull can be (meaningfully) calculated.
 * 
 * @author Pete Bankhead
 *
 */
public interface ROIWithHull extends ROI {

	/**
	 * Get a PathArea representing the convex hull of this ROI.
	 * <p>
	 * This is the smallest convex shape that may completely contain the ROI.
	 * @return
	 */
	public PathArea getConvexHull();
	
	/**
	 * Get the area of the convex hull of this ROI in pixels^2.
	 * @return
	 */
	public double getConvexArea();

	/**
	 * Get the area of the convex hull of this ROI in scaled units^2.
	 * @return
	 */
	public double getScaledConvexArea(double pixelWidth, double pixelHeight);

}
