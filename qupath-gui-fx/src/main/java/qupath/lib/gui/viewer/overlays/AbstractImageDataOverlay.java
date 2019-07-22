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

import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Abstract implementation of an ImageDataOverlay.
 * 
 * @author Pete Bankhead
 *
 */
public abstract class AbstractImageDataOverlay extends AbstractOverlay implements ImageDataOverlay {

	private ImageData<BufferedImage> imageData;
	
	public AbstractImageDataOverlay(final OverlayOptions overlayOptions, final ImageData<BufferedImage> imageData) {
		this.overlayOptions = overlayOptions;
		this.imageData = imageData;
	}

	@Override
	public void setImageData(final ImageData<BufferedImage> imageData) {
		if (this.imageData == imageData)
			return;
		if (!supportsImageDataChange())
			this.imageData = null;
		else
			this.imageData = imageData;
	}
	
	protected PathObjectHierarchy getHierarchy() {
		return imageData == null ? null : imageData.getHierarchy();
	}
	
	protected ImageData<BufferedImage> getImageData() {
		return imageData;
	}
	
	protected ImageServer<BufferedImage> getServer() {
		return imageData == null ? null : imageData.getServer();
	}

}