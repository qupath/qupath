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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.images.ImageData;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.regions.ImageRegion;


/**
 * An overlay capable of painting a TMA Grid.
 * 
 * @see HierarchyOverlay
 * 
 * @author Pete Bankhead
 *
 */
public class TMAGridOverlay extends AbstractImageDataOverlay {

	/**
	 * Constructor.
	 * @param overlayOptions overlay options to control the display of this overlay.
	 * @param imageData the current image data
	 */
	public TMAGridOverlay(final OverlayOptions overlayOptions, final ImageData<BufferedImage> imageData) {
		super(overlayOptions, imageData);
	}

	@Override
	public boolean isInvisible() {
		return super.isInvisible() || !getOverlayOptions().getShowTMAGrid();
	}
	
	@Override
	public void paintOverlay(final Graphics2D g, final ImageRegion imageRegion, final double downsampleFactor, final ImageObserver observer, final boolean paintCompletely) {
		if (isInvisible())
			return;

		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null)
			return;
		
		TMAGrid tmaGrid = hierarchy.getTMAGrid();
		if (tmaGrid == null)
			return;

		
		Graphics2D g2d = (Graphics2D)g.create();
		// Set alpha composite if needed
		setAlphaComposite(g2d);
		
//		Rectangle serverBounds = imageRegion.getBounds();
		
		// Ensure antialias is on...?
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		PathHierarchyPaintingHelper.paintTMAGrid(g2d, tmaGrid, getOverlayOptions(), hierarchy.getSelectionModel(), downsampleFactor);
		
		g2d.dispose();
	}

	@Override
	public boolean supportsImageDataChange() {
		return true;
	}	


}
