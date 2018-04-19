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

package qupath.lib.images;

import qupath.lib.regions.ImageRegion;

/**
 * Interface used when wanting to store pixel data, in some format dependent on {@code <T>} (e.g. BufferedImage, ImagePlus, Mat...), along
 * with information of the image from which the pixel data was obtained, including the downsample factor used to extract it.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface PathImage<T> {

	public String getImageTitle();
	
	/**
	 * Get the pixel data (image)
	 * @return
	 */
	public T getImage();
	
	/**
	 * Get the downsample factor originally used when obtaining the image from an ImageServer; will be 1 if the image is full-resolution.
	 * @return
	 */
	public double getDownsampleFactor();

	/**
	 * Check if the image is available; if this returns false, then getImage() should read the image lazily on demand
	 * @return
	 */
	public boolean hasCachedImage();
	
	/**
	 * Version of getImage() that makes it possible to specified whether the image should be cached or not;
	 * if the pixel data will not be required again, getImage(false) may improve efficiency.
	 * <p>
	 * This only makes a difference is the image is not already cached, i.e. hasCachedImage() returns false.
	 * @return
	 */
	public T getImage(boolean cache);
	
	/**
	 * Test whether getPixelWidthMicrons() is equal to getPixelHeightMicrons() (with a small floating-point tolerance).
	 * @return true if the pixels are approximately square, false otherwise
	 */
	public boolean validateSquarePixels();
	
	/**
	 * Get the horizontal pixel size, in microns, or Double.NaN if this is unavailable.
	 * @return
	 */
	public double getPixelWidthMicrons();
	
	/**
	 * Get the vertical pixel size, in microns, or Double.NaN if this is unavailable.
	 * @return
	 */
	public double getPixelHeightMicrons();
	
	/**
	 * Query whether the horizontal &amp; vertical pixel sizes are available in microns.
	 * @return true if both getPixelWidthMicrons() and getPixelHeightMicrons() return non-NaN values; false otherwise.
	 */
	public boolean hasPixelSizeMicrons();
	
	/**
	 * The region within the (original, possibly larger) image that this particular image corresponds to.
	 * @return
	 */
	public ImageRegion getImageRegion();
	
}
