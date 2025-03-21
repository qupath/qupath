/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020, 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.images.stores;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Interface for objects capable of converting a {@link BufferedImage} for rendering using {@link Graphics2D}.
 * This typically means applying any color transforms to produce an (A)RGB image.
 * <p>
 * A timestamp and ID are supplied to help with caching.
 * 
 * @author Pete Bankhead
 */
public interface ImageRenderer {
	
	/**
	 * Apply the required transforms to a BufferedImage to get the appropriate display.
	 * imgOutput should always be an RGB image (of some kind), or null if a new image should be created.
	 * 
	 * imgInput should always be an image of the kind that matches the imgData, e.g. RGB/non-RGB, same number of channels,
	 * same bit-depth.
	 * 
	 * @param imgInput input image
	 * @param imgOutput output image, with the same width and height as the input; 
	 *        if null or the image size is inconsistent, a new RGB image should be created
	 * @return imgOutput, or a new RGB image created for the output
	 */
	BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput);
	
	/**
	 * Timestamp of the last change.
	 * The format should not be assumed - only that the value increases.
	 * This means that it might be in nanoseconds, milliseconds or 'events' (i.e. an incrementing value each time
	 * there is a change).
	 * <p>
	 * This is useful to identify when the status has changed, for example to determine whether a repaint is necessary.
	 * @return the value of the timestamp
	 */
	long getLastChangeTimestamp();
	
	/**
	 * Get a unique key, which will be used for caching.
	 * <p>
	 * The only requirement is that the key is unique for the {@code ImageRenderer} in its 
	 * current state.  It is suggested to base it on the full class name, a counter for instances 
	 * of this class, and a timestamp derived from the last change.
	 * 
	 * @return
	 */
	String getUniqueID();
	
}