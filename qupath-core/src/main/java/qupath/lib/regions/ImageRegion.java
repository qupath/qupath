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

package qupath.lib.regions;

import qupath.lib.roi.interfaces.ROI;

/**
 * Class for defining an image region.
 * A boundary box is given in pixel coordinates, while z &amp; t values are given as indices.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageRegion {
	
	private final int x;
	private final int y;
	private final int width;
	private final int height;
	
	private final int z;
	private final int t;
	
	
	@Override
	public String toString() {
		return "Region: x=" + x + ", y=" + y + ", w=" + width + ", h=" + height + ", z=" + z + ", t=" + t;
	}
	
	ImageRegion(final int x, final int y, final int width, final int height, final int z, final int t) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.z = z;
		this.t = t;
	}
	
	public static ImageRegion createInstance(final int x, final int y, final int width, final int height, final int z, final int t) {
		return new ImageRegion(x, y, width, height, z, t);
	}
	
	public static ImageRegion createInstance(final ROI pathROI) {
		int x1 = (int)pathROI.getBoundsX();
		int y1 = (int)pathROI.getBoundsY();
		int x2 = (int)Math.ceil(pathROI.getBoundsX() + pathROI.getBoundsWidth());
		int y2 = (int)Math.ceil(pathROI.getBoundsY() + pathROI.getBoundsHeight());
		return ImageRegion.createInstance(x1, y1, x2-x1, y2-y1, pathROI.getZ(), pathROI.getT());
	}
	
	/**
	 * Returns true if the region specified by this request overlaps with that of another request.
	 * The test includes insuring that they refer to the same image.
	 * 
	 * @param request
	 * @return
	 */
	public boolean intersects(final ImageRegion request) {
		return	(z < 0 || z == request.z || request.z < 0) && (t < 0 || t == request.t || request.t < 0) &&
				intersects(request.x, request.y, request.width, request.height);
	}
	
	public boolean intersects(final double x2, final double y2, final double w2, final double h2) {
		if (w2 <= 0 || h2 <= 0)
            return false;		
        return (x2 + w2 > x &&
                y2 + h2 > y &&
                x2 < x + width &&
                y2 < y + height);
//		return new Rectangle(x, y, width, height).intersects(x2, y2, w2, h2);
	}

	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	public int getZ() {
		return z;
	}

	public int getT() {
		return t;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + height;
		result = prime * result + t;
		result = prime * result + width;
		result = prime * result + x;
		result = prime * result + y;
		result = prime * result + z;
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ImageRegion other = (ImageRegion) obj;
		if (height != other.height)
			return false;
		if (t != other.t)
			return false;
		if (width != other.width)
			return false;
		if (x != other.x)
			return false;
		if (y != other.y)
			return false;
		if (z != other.z)
			return false;
		return true;
	}
	
}
