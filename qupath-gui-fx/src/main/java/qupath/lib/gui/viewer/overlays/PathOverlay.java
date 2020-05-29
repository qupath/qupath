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

package qupath.lib.gui.viewer.overlays;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import qupath.lib.images.ImageData;
import qupath.lib.regions.ImageRegion;


/**
 * Interface defining an overlay to paint on top of a viewer.
 * 
 * @author Pete Bankhead
 *
 */
public interface PathOverlay {
	
	/**
	 * Paint the overlay to a graphics object.  The graphics object will have a transform applied to it, so the painting should
	 * make use of coordinates in the original image space.
	 * 
	 * @param g2d Graphics2D object to which drawing should be performed. This should have any transform already applied to it.
	 * @param imageRegion The maximum image region that should be shown.
	 * @param downsampleFactor The downsample factor at which the overlay will be viewed.  There is no need for rescaling according to
	 * 							this value since it has already been applied to the {@link Graphics2D} as part of its {@link AffineTransform}, however
	 * 							it may optionally be needed within the method e.g. to correct line thicknesses.
	 * @param imageData the {@link ImageData} associated with this overlay. If the overlay is being displayed on a viewer, this is the {@link ImageData} open 
	 * 					within the viewer. Not all overlays require this, and it may be null.
	 * @param paintCompletely If true, the method is permitted to return without completely painting everything, for performance reasons.
	 */
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely);


}
