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

package qupath.opencv.features;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.gui.viewer.overlays.AbstractImageDataOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObjectConnectionGroup;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImageRegion;

/**
 * Overlay to show Delaunay triangulation.
 * 
 * This is an initial implementation that has a substantial performance impact... repainting isn't very fast
 * when large numbers of lines need to be drawn.
 * 
 * Hopefully this situation will improve in the future...
 * 
 * @author Pete Bankhead
 *
 */
public class DelaunayOverlay extends AbstractImageDataOverlay {
	
	private PathObjectConnections triangulations = new PathObjectConnections();

	public DelaunayOverlay(OverlayOptions overlayOptions, ImageData<BufferedImage> imageData) {
		super(overlayOptions, imageData);
	}

	@Override
	public boolean supportsImageDataChange() {
		return false;
	}

	@Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {
		if (isInvisible() || triangulations == null || triangulations.isEmpty() || downsampleFactor >= 20)
			return;
				
		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null)
			return;

		PathHierarchyPaintingHelper.paintConnections(triangulations, hierarchy, g2d, getPreferredOverlayColor(), downsampleFactor);
	}
	
	
	public void addDelaunay(final PathObjectConnectionGroup dt) {
		triangulations.addGroup(dt);
	}

	public void removeDelaunay(final PathObjectConnectionGroup dt) {
		triangulations.removeGroup(dt);
	}
	
	public void clear() {
		triangulations.clear();
	}


}
