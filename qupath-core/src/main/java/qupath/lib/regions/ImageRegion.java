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

package qupath.lib.regions;

import java.util.Collection;

import qupath.lib.roi.interfaces.ROI;

/**
 * Class for defining an image region.
 * <p>
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
	
	/**
	 * Create a region based on its bounding box coordinates, z-slice index and time point index.
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param z
	 * @param t
	 * @return
	 */
	public static ImageRegion createInstance(final int x, final int y, final int width, final int height, final int z, final int t) {
		if (width < 0)
			throw new IllegalArgumentException("Width must be >= 0! Requested width = " + width);
		if (height < 0)
			throw new IllegalArgumentException("Height must be >= 0! Requested height = " + height);
		return new ImageRegion(x, y, width, height, z, t);
	}
	
	/**
	 * Create the smallest region that completely contains a specified ROI.
	 * @param pathROI
	 * @return
	 */
	public static ImageRegion createInstance(final ROI pathROI) {
		int x1 = (int)pathROI.getBoundsX();
		int y1 = (int)pathROI.getBoundsY();
		int x2 = (int)Math.ceil(pathROI.getBoundsX() + pathROI.getBoundsWidth());
		int y2 = (int)Math.ceil(pathROI.getBoundsY() + pathROI.getBoundsHeight());
		return ImageRegion.createInstance(x1, y1, x2-x1, y2-y1, pathROI.getZ(), pathROI.getT());
	}
	
	/**
	 * Create the smallest region that completely contains the specified ROIs.
	 * @param rois
	 * @return
	 */
	public static ImageRegion createInstance(final Collection<? extends ROI> rois) {
		if (rois.isEmpty())
			return new ImageRegion(0, 0, 0, 0, 0, 0);
		if (rois.size() == 1)
			return createInstance(rois.iterator().next());
		double xMin = Double.POSITIVE_INFINITY;
		double xMax = Double.NEGATIVE_INFINITY;
		double yMin = Double.POSITIVE_INFINITY;
		double yMax = Double.NEGATIVE_INFINITY;
		ImagePlane plane = null;
		for (var roi : rois) {
			if (plane == null)
				plane = roi.getImagePlane();
			else if (plane.getT() != roi.getT() || plane.getZ() != roi.getZ())
				throw new IllegalArgumentException("Failed to create ImageRegion for multiple ROIs, ImagePlanes do not match!");
			xMin = Math.min(xMin, roi.getBoundsX());
			yMin = Math.min(yMin, roi.getBoundsY());
			xMax = Math.max(xMax, roi.getBoundsX() + roi.getBoundsWidth());
			yMax = Math.max(yMax, roi.getBoundsY() + roi.getBoundsHeight());
		}
		int x1 = (int)Math.floor(xMin);
		int y1 = (int)Math.floor(yMin);
		int x2 = (int)Math.ceil(xMax);
		int y2 = (int)Math.ceil(yMax);
		return ImageRegion.createInstance(x1, y1, x2-x1, y2-y1, plane.getZ(), plane.getT());
	}
	
	/**
	 * Returns true if the region specified by this region overlaps with another.
	 * <p>
	 * If either z or t is &lt; 0 then that value will be ignored.
	 * 
	 * @param request
	 * @return
	 */
	public boolean intersects(final ImageRegion request) {
		return	(z < 0 || z == request.z || request.z < 0) && (t < 0 || t == request.t || request.t < 0) &&
				intersects(request.x, request.y, request.width, request.height);
	}
	
	/**
	 * Query if this region intersects with a specified bounding box, ignoring z-slice and time point information.
	 * @param x2
	 * @param y2
	 * @param w2
	 * @param h2
	 * @return
	 */
	public boolean intersects(final double x2, final double y2, final double w2, final double h2) {
		if (w2 <= 0 || h2 <= 0)
            return false;		
        return (x2 + w2 > x &&
                y2 + h2 > y &&
                x2 < x + width &&
                y2 < y + height);
	}

	/**
	 * Check if this region contains a specified coordinate.
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @param t
	 * @return
	 */
	public boolean contains(int x, int y, int z, int t) {
		return getZ() == z &&
			   getT() == t &&
			   x >= getX() &&
			   x < getX() + getWidth() &&
			   y >= getY() &&
			   y < getY() + getHeight();
	}
	
	/**
	 * Get the x coordinate of the region bounding box (top left).
	 * @return
	 */
	public int getX() {
		return x;
	}
	
	/**
	 * Get the y coordinate of the region bounding box (top left).
	 * @return
	 */
	public int getY() {
		return y;
	}
	
	/**
	 * Get the width of the region bounding box.
	 * @return
	 */
	public int getWidth() {
		return width;
	}
	
	/**
	 * Get the height of the region bounding box.
	 * @return
	 */
	public int getHeight() {
		return height;
	}
	
	/**
	 * Get the z-slice index for the region.
	 * @return
	 */
	public int getZ() {
		return z;
	}

	/**
	 * Get the time point index for the region.
	 * @return
	 */
	public int getT() {
		return t;
	}
	
	
	/**
	 * Get the x coordinate of the top left of the region bounding box.
	 * @return
	 */
	public int getMinX() {
		return Math.min(getX(), getX() + getWidth());
	}

	/**
	 * Get the x coordinate of the bottom right of the region bounding box.
	 * @return
	 */
	public int getMaxX() {
		return Math.max(getX(), getX() + getWidth());
	}
	
	/**
	 * Get the y coordinate of the top left of the region bounding box.
	 * @return
	 */
	public int getMinY() {
		return Math.min(getY(), getY() + getHeight());
	}

	/**
	 * Get the y coordinate of the bottom right of the region bounding box.
	 * @return
	 */
	public int getMaxY() {
		return Math.max(getY(), getY() + getHeight());
	}

	/**
	 * Get the z-slice and time point for this region as an {@link ImagePlane}.
	 * @return
	 */
	public ImagePlane getPlane() {
		return ImagePlane.getPlane(getZ(), getT());
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
