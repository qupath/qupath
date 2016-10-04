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
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.List;

import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * 
 * Note: This overlay has some performance issues, and is really only 
 * a temporary solution while QuPath awaits both a more complete overlay 'layer' system, 
 * and a more permanent data structure for representing (triangulated) relationships 
 * between PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
public class DelaunayOverlay extends AbstractImageDataOverlay {
	
	private List<PathObject> p1List = new ArrayList<>();
	private List<PathObject> p2List = new ArrayList<>();
	private List<PathObject> p3List = new ArrayList<>();
	private int n = 0;

	public DelaunayOverlay(OverlayOptions overlayOptions, ImageData<BufferedImage> imageData) {
		super(overlayOptions, imageData);
	}

	@Override
	public boolean supportsImageDataChange() {
		return false;
	}

	@Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {
		if (isInvisible() || n == 0)
			return;
		
		Shape clipShape = g2d.getClip();
		
		g2d.setStroke(PathHierarchyPaintingHelper.getCachedStroke(PathPrefs.getThinStrokeThickness()));
		g2d.setColor(getPreferredOverlayColor());
		
		Line2D line = new Line2D.Float();
		for (int i = 0; i < n; i++) {
			PathObject p1 = p1List.get(i);
			PathObject p2 = p2List.get(i);
			PathObject p3 = p3List.get(i);
			
			ROI r1 = p1 == null ? null : p1.getROI();
			ROI r2 = p2 == null ? null : p2.getROI();
			ROI r3 = p3 == null ? null : p3.getROI();
			
			if ((r1 != null && !clipShape.contains(r1.getCentroidX(), r1.getCentroidY())) && 
					(r2 != null && !clipShape.contains(r2.getCentroidX(), r2.getCentroidY())) &&
					(r3 != null && !clipShape.contains(r3.getCentroidX(), r3.getCentroidY())))
				continue;
			
			drawLine(g2d, r1, r2, line);
			drawLine(g2d, r2, r3, line);
			drawLine(g2d, r1, r3, line);
		}
		
	}
	
	
	void drawLine(Graphics2D g, ROI r1, ROI r2, Line2D lineCached) {
		if (r1 == null || r2 == null)
			return;
		lineCached.setLine(r1.getCentroidX(), r1.getCentroidY(), r2.getCentroidX(), r2.getCentroidY());
		g.draw(lineCached);
	}
	
	
	public void addTriangle(PathObject p1, PathObject p2, PathObject p3) {
		p1List.add(p1);
		p2List.add(p2);
		p3List.add(p3);
		n++;
	}
	

}
