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

package qupath.lib.analysis.images;

/**
 * A minimal interface to define a means to provide access to pixel values from a 2D, 1-channel image.
 * 
 * @author Pete Bankhead
 *
 */
public interface SimpleModifiableImage extends SimpleImage {
	
	/**
	 * Set the value of a single pixel.
	 * @param x x-coordinate of the pixel to set
	 * @param y y-coordinate of the pixel to set
	 * @param val new pixel value
	 */
	public void setValue(int x, int y, float val);
	
	/**
	 * Request the pixel array representing all the pixels in this image, returned row-wise.
	 * @param direct if true, the internal array will be returned if possible 
	 * @return
	 */
	float[] getArray(boolean direct);
	
}