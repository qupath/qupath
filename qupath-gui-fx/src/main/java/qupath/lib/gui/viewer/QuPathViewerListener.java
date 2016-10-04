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
	
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew);	

	public void visibleRegionChanged(QuPathViewer viewer, Shape shape);	

	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected);
	
	public void viewerClosed(QuPathViewer viewer);

}
