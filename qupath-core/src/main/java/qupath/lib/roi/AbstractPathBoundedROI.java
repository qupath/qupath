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

import qupath.lib.regions.ImagePlane;

/**
 * Abstract implementation of any ROI that can be defined based on a bounding box, 
 * i.e. a rectangle or ellipse (both unrotated).
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractPathBoundedROI extends AbstractPathROI {
	
	protected double x, y, x2, y2;
	
	/**
	 * Constructor that sets the ROI to being modified from the start (i.e. initialized from a single mouse event)
	 * @param x
	 * @param y
	 */
	AbstractPathBoundedROI(double x, double y, ImagePlane plane) {
		this(x, y, 0, 0, plane);
	}
	
	AbstractPathBoundedROI(double x, double y) {
		this(x, y, 0, 0, null);
	}
	
	AbstractPathBoundedROI() {
		super();
	}
	
	AbstractPathBoundedROI(double x, double y, double width, double height, ImagePlane plane) {
		super(plane);
		this.x = x;
		this.y = y;
		this.x2 = x + width;
		this.y2 = y + height;
		ensureOrder();
	}
	
	void ensureOrder() {
		if (x2 < x) {
			double temp = x;
			x = x2;
			x2 = temp;
		}
		if (y2 < y) {
			double temp = y;
			y = y2;
			y2 = temp;
		}
	}
	
	
//	public void updateAdjustment(double xx, double yy, boolean shiftDown) {
//		if (isAdjusting) {
//			// Update x & y
//			// If pressing shift, constrain to be square
//			if (shiftDown) {
//				double w = x - xx;
//				double h = y - yy;
//				if (w != 0 && h != 0) {
//					double len = Math.min(Math.abs(w), Math.abs(h));
//					w = Math.signum(w) * len;
//					h = Math.signum(h) * len;
//				}
//				x2 = x - w;
//				y2 = y - h;		
//			} else {
//				x2 = xx;
//				y2 = yy;
//			}
//		}
//	}
	
	
//	public void finishAdjusting(double x, double y, boolean shiftDown) {
//		super.finishAdjusting(x, y, shiftDown);
//		ensureOrder();
//	}
	
	
//	public boolean translate(double dx, double dy) {
//		// Shift the bounds
//		x += dx;
//		y += dy;
//		x2 += dx;
//		y2 += dy;
//		return dx != 0 || dy != 0;
//	}
	
	@Override
	public double getCentroidX() {
		return (x + x2) * 0.5;
	}

	@Override
	public double getCentroidY() {
		return (y + y2) * 0.5;
	}
	
	@Override
	public abstract boolean contains(double x, double y);
	
	@Override
	public boolean isEmpty() {
		return x == x2 || y == y2;
	}
	
	@Override
	public double getBoundsX() {
		return x < x2 ? x : x2;
	}
	
	@Override
	public double getBoundsY() {
		return y < y2 ? y : y2;
	}
	
	@Override
	public double getBoundsWidth() {
		return Math.abs(x - x2);
	}

	@Override
	public double getBoundsHeight() {
		return Math.abs(y - y2);
	}
	
		
}