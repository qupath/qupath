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
 * Interface defining measurements that can be provided by ROIs that represent 1D lines.
 * <p>
 * At the time of writing, this only relates to LineROIs (single start and end points), but
 * could also be applied to polylines in the future.
 * 
 * @author Pete Bankhead
 */
public interface PathLine extends PathShape {
	
	/**
	 * Get line length in pixels, i.e. sqrt((x2-x1)^2 + (y2-y1)^2).
	 * @return
	 */
	public double getLength();

	/**
	 * Get line length in scaled units, given a specified pixel width &amp; pixel height.
	 * @param pixelWidth
	 * @param pixelHeight
	 * @return
	 */
	public double getScaledLength(double pixelWidth, double pixelHeight);

}
