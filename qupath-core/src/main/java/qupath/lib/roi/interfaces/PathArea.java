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
 * Interface defining measurements that can be provided by ROIs that represent 2D areas.
 * 
 * @author Pete Bankhead
 */
public interface PathArea extends PathShape {
	
	/**
	 * Get ROI area in pixels^2, or Double.NaN if no area can be computed.
	 * @return
	 */
	public double getArea();
	
	/**
	 * Get ROI perimeter in pixels, or Double.NaN if no perimeter can be computed.
	 * @return
	 */
	public double getPerimeter();

	/**
	 * Get circularity measurement, calculated as 4 * PI * (area / perimeter^2)
	 * <p>
	 * This ranges between 0 (for a line) and 1 (for a circle).  Note that the pixel (i.e. not scaled) areas and perimeters are used.
	 * @return
	 */
	public double getCircularity();

	/**
	 * Get circularity measurement, calculated as 4 * PI * (area / perimeter^2).
	 * <p>
	 * This ranges between 0 (for a line) and 1 (for a circle).  This version optionally allows non-square pixels to be used.
	 * @return
	 */
	public double getCircularity(double pixelWidth, double pixelHeight);

	/**
	 * Get ROI area in scaled units^2 given a specified pixel width &amp; pixel height, or Double.NaN if no area can be computed.
	 * @param pixelWidth
	 * @param pixelHeight
	 * @return
	 */
	public double getScaledArea(double pixelWidth, double pixelHeight);

	/**
	 * Get ROI perimeter in scaled units given a specified pixel width &amp; pixel height, or Double.NaN if no area can be computed.
	 * @param pixelWidth
	 * @param pixelHeight
	 * @return
	 */
	public double getScaledPerimeter(double pixelWidth, double pixelHeight);

	/**
	 * Test is the ROI contains specified x, y coordinates.
	 * @param x
	 * @param y
	 * @return
	 */
	public boolean contains(double x, double y);
	
}
