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

package qupath.lib.images;

import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.ImageRegion;

/**
 * Interface used when needing to associate pixel data with information regarding the image from which it was obtained.
 * <p>
 * The generic parameter defines the type of the image (e.g. BufferedImage, ImagePlus, Mat...).
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface PathImage<T> {
	
	/**
	 * Get the pixel data (image).
	 * 
	 * @return
	 */
	public T getImage();
	
	/**
	 * Get the downsample factor originally used when obtaining the image from an ImageServer; will be 1 if the image is full-resolution.
	 * @return
	 */
	public double getDownsampleFactor();
	
	/**
	 * Get the PixelCalibration representing actual pixel sizes in this image, with downsampling applied if necessary.
	 * @return
	 */
	public PixelCalibration getPixelCalibration();
	
	/**
	 * The region within the (original, possibly larger) image that this particular image corresponds to.
	 * @return
	 */
	public ImageRegion getImageRegion();
	
}
