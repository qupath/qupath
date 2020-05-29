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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
public class TMAGridOverlay extends AbstractOverlay {

	/**
	 * Constructor.
	 * @param overlayOptions overlay options to control the display of this overlay.
	 */
	public TMAGridOverlay(final OverlayOptions overlayOptions) {
		super(overlayOptions);
	}

	@Override
	public boolean isVisible() {
		return super.isVisible() && getOverlayOptions().getShowTMAGrid();
	}
	
	@Override
	public void paintOverlay(final Graphics2D g, final ImageRegion imageRegion, final double downsampleFactor, final ImageData<BufferedImage> imageData, final boolean paintCompletely) {
		if (!isVisible())
			return;

		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();
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


}
