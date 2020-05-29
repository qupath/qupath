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

package qupath.lib.gui.viewer;

import java.util.EventListener;
import java.awt.Shape;
import java.awt.image.BufferedImage;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

/**
 * Interface for defining a listener that needs to know whenever the data 
 * related to a QuPathViewer has changed in some way - such as by changing 
 * the underlying ImageData, or by moving the field of view.
 * 
 * @author Pete Bankhead
 *
 */
public interface QuPathViewerListener extends EventListener {
	
	/**
	 * Called with the image data within a viewer has changed.
	 * @param viewer the viewer whose image has changed
	 * @param imageDataOld the image previously open in the viewer
	 * @param imageDataNew the image now open in the viewer
	 */
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew);	

	/**
	 * Called when the visible region has changed in a viewer.
	 * @param viewer the viewer whose visible region hs changed.
	 * @param shape shape representing the new visible region (in image pixel coordinates).
	 *              This is rectangular, but may also be rotated.
	 */
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape);	

	/**
	 * Called when the primary selected object has changed in a viewer.
	 * @param viewer the viewer
	 * @param pathObjectSelected
	 */
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected);
	
	/**
	 * Called when a viewer is closed.
	 * @param viewer the viewer that has been closed.
	 */
	public void viewerClosed(QuPathViewer viewer);

}
