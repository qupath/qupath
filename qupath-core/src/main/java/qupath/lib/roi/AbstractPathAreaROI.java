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

package qupath.lib.roi;

import qupath.lib.roi.interfaces.PathArea;

/**
 * Abstract implementation of any ROI representing an area (i.e. not a line).
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractPathAreaROI extends AbstractPathROI implements PathArea {
	
	AbstractPathAreaROI() {
		super();
	}
	
	AbstractPathAreaROI(int c, int z, int t) {
		super(c, z, t);
	}

	// Measurements
	@Override
	public double getCircularity() {
		double p = getPerimeter();
		return 4 * Math.PI * (getArea() / (p*p));
	}
	
	@Override
	public double getCircularity(double pixelWidth, double pixelHeight) {
		double p = getScaledPerimeter(pixelWidth, pixelHeight);
		return 4 * Math.PI * (getScaledArea(pixelWidth, pixelHeight) / (p*p));
	}
	
	
	/**
	 * TRUE if the ROI has zero area
	 */
	@Override
	public boolean isEmpty() {
		return getArea() <= 0;
	}
	
	@Override
	public double getArea() {
		return getScaledArea(1, 1);
	}
	
	@Override
	public double getPerimeter() {
		return getScaledPerimeter(1, 1);
	}

	@Override
	public RoiType getRoiType() {
		return RoiType.AREA;
	}

}
