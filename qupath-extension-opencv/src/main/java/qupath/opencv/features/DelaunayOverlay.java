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

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.gui.viewer.overlays.AbstractImageDataOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

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
	
	private Set<PathObjectConnections> triangulations = Collections.synchronizedSet(new LinkedHashSet<>());

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

		float alpha = (float)(1f - downsampleFactor / 15);
		alpha = Math.min(alpha, 0.5f);
		if (alpha < .05f)
			return;

		g2d = (Graphics2D)g2d.create();

//		Shape clipShape = g2d.getClip();
		g2d.setStroke(PathHierarchyPaintingHelper.getCachedStroke(PathPrefs.getThinStrokeThickness()));
		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * .5f));
//		g2d.setColor(ColorToolsAwt.getColorWithOpacity(getPreferredOverlayColor(), 1));
		g2d.setColor(getPreferredOverlayColor());
		Line2D line = new Line2D.Double();
		imageRegion = AwtTools.getImageRegion(g2d.getClipBounds(), 0, 0);
		
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		
		
		Collection<PathObject> pathObjects = hierarchy.getObjectsForRegion(PathDetectionObject.class, imageRegion, null);
//		double threshold = downsampleFactor*downsampleFactor*4;
		for (PathObjectConnections dt : triangulations) {
			for (PathObject pathObject : pathObjects) {
				ROI roi = PathObjectTools.getROI(pathObject, true);
				for (PathObject siblingObject : dt.getConnectedObjects(pathObject)) {
					ROI roi2 = PathObjectTools.getROI(siblingObject, true);
					line.setLine(roi.getCentroidX(), roi.getCentroidY(), roi2.getCentroidX(), roi2.getCentroidY());
					g2d.draw(line);
				}
			}
		}
		
		g2d.dispose();
	}
	
	
	public void addDelaunay(final PathObjectConnections dt) {
		triangulations.add(dt);
	}

	public void removeDelaunay(final PathObjectConnections dt) {
		triangulations.remove(dt);
	}
	
	public void clear() {
		triangulations.clear();
	}


}
