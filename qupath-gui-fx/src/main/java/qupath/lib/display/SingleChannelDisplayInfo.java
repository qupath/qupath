/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.display;

import java.awt.image.BufferedImage;

import qupath.lib.images.ImageData;

/**
 * {@link ChannelDisplayInfo} that determines colors based upon a single value for each pixel.
 * This is able to supply the underlying values as a float array.
 * 
 * @author Pete Bankhead
 */
public interface SingleChannelDisplayInfo extends ChannelDisplayInfo {
	
	/**
	 * Extract the value for a single pixel of an image.
	 * @param img the image
	 * @param x x-coordinate of the pixel
	 * @param y y-coordinate of the pixel
	 * @return the value of the pixel
	 */
	public abstract float getValue(BufferedImage img, int x, int y);
	
	/**
	 * Extract values for a square of pixels from an image.
	 * 
	 * @param img the image
	 * @param x x-coordinate of the top left corner of the region to extract
	 * @param y y-coordinate of the top left corner of the region to extract
	 * @param w width of the region to extract
	 * @param h height of the region to extract
	 * @param array optional array that may be used to store the output values
	 * @return array of values
	 */
	public abstract float[] getValues(BufferedImage img, int x, int y, int w, int h, float[] array);
	
	/**
	 * Check if {@link #getValue(BufferedImage, int, int)} returns fixed values, or if they are dependent on 
	 * other properties of the {@link ImageData}.
	 * <p>
	 * For example, a transform based on color deconvolution should be flagged as mutable because stain vectors change, 
	 * while a simple channel separation is not considered mutable (since the pixel values for the underlying image remain constant in QuPath).
	 * @return
	 */
	public boolean isMutable();
	
}