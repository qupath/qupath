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

package qupath.lib.gui.viewer.overlays;

import java.awt.image.BufferedImage;

import qupath.lib.images.ImageData;

/**
 * A PathOverlay that makes use of an ImageData for its display.
 * 
 * @author Pete Bankhead
 *
 */
public interface ImageDataOverlay extends PathOverlay {

	/**
	 * Notify the overlay that any associated ImageData has changed.  The overlay may or may not
	 * care about this.
	 * <p>
	 * Note: This 
	 * <p>
	 * TODO: Consider whether overlays should take care of their own ImageData changes.
	 * @param imageData
	 */
	public void setImageData(ImageData<BufferedImage> imageData);
	
	/**
	 * Check if an ImageData change is supported.  Some overlays only make sense with specific
	 * ImageDatas (e.g. if connected to a specific set of detected objects), and should be
	 * removed if this changes.
	 * @return
	 */
	public boolean supportsImageDataChange();
	
}
