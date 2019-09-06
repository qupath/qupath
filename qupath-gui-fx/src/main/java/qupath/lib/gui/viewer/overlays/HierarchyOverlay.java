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

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.gui.images.servers.PathHierarchyImageServer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.DefaultPathObjectConnectionGroup;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;


/**
 * An overlay capable of painting a PathObjectHierarchy, *except* for any 
 * TMA grid (which is handled by TMAGridOverlay).
 * 
 * TODO: Reconsider separation of hierarchy-drawing overlays whenever a more complete 
 * 'layer' system is in place.
 * 
 * @author Pete Bankhead
 *
 */
public class HierarchyOverlay extends AbstractImageDataOverlay {
	
	final static private Logger logger = LoggerFactory.getLogger(HierarchyOverlay.class);

	private PathHierarchyImageServer overlayServer = null;

	private DefaultImageRegionStore regionStore = null;
	private boolean smallImage = false; // If the image is small enough, objects should be drawn directly
	
	transient private DetectionComparator comparator = new DetectionComparator();

	public HierarchyOverlay(final DefaultImageRegionStore regionStore, final OverlayOptions overlayOptions, final ImageData<BufferedImage> imageData) {
		super(overlayOptions, imageData);
		this.regionStore = regionStore;
		updateOverlayServer();
	}


	@Override
	public void setImageData(final ImageData<BufferedImage> imageData) {
		if (getImageData() == imageData)
			return;
		super.setImageData(imageData);
		updateOverlayServer();
	}
	
	
	void updateOverlayServer() {
		clearCachedOverlay();
		if (getImageData() == null)
			overlayServer = null;
		else {
			ImageServer<BufferedImage> server = getImageData().getServer();
			// If the image is small, don't really need a server at all...
			overlayServer = new PathHierarchyImageServer(getImageData(), getOverlayOptions());
//			overlayServer = new PathHierarchyImageServer(server, getHierarchy(), getOverlayOptions());
			smallImage = server.getWidth() < PathPrefs.getMinWholeSlideDimension() && server.getHeight() < PathPrefs.getMinWholeSlideDimension();
		}
	}

	
	@Override
	public boolean isInvisible() {
		return super.isInvisible() || getImageData() == null;
	}
	

	@Override
	public void paintOverlay(final Graphics2D g2d, final ImageRegion imageRegion, final double downsampleFactor, final ImageObserver observer, final boolean paintCompletely) {
		
		// Get the selection model, which can influence colours (TODO: this might not be the best way to do it!)
		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null)
			return;
		
		if (isInvisible() && hierarchy.getSelectionModel().noSelection())
			return;

		Rectangle serverBounds = AwtTools.getBounds(imageRegion);
		int t = imageRegion.getT();
		int z = imageRegion.getZ();
		
		// Ensure antialias is on...?
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		
		// Get the displayed clip bounds for fast checking if ROIs need to be drawn
		Shape shapeRegion = g2d.getClip();
		if (shapeRegion == null)
			shapeRegion = AwtTools.getBounds(imageRegion);
		Rectangle boundsDisplayed = shapeRegion.getBounds();
		// Ensure the bounds do not extend beyond what the server actually contains
		//			if (boundsDisplayed.getMinX() < serverBounds.getMinX() || boundsDisplayed.getMaxX() > serverBounds.getMaxX() || boundsDisplayed.getMinY() < serverBounds.getMinY() || boundsDisplayed.getMaxY() > serverBounds.getMaxY())
		boundsDisplayed = boundsDisplayed.intersection(serverBounds);
		if (boundsDisplayed.width <= 0 || boundsDisplayed.height <= 0)
			return;
		ImageRegion region = AwtTools.getImageRegion(boundsDisplayed, z, t);
		//		System.out.println("Displayed clip: " + clip);

		// Paint detection objects
		long startTime = System.currentTimeMillis();

		// TODO: Cache detections on an overlay image for faster repainting e.g. when drawing ROIs
		OverlayOptions overlayOptions = getOverlayOptions();
		if (overlayOptions.getShowDetections() && !hierarchy.isEmpty()) {

			// If we aren't downsampling by much, or we're upsampling, paint directly - making sure to paint the right number of times, and in the right order
			if (smallImage || overlayServer == null || regionStore == null || downsampleFactor < 1.0) {
				Set<PathObject> pathObjectsToPaint = new TreeSet<>(comparator);
				Collection<PathObject> pathObjects = hierarchy.getObjectsForRegion(PathDetectionObject.class, region, pathObjectsToPaint);
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
				PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, pathObjects, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);
				
				if (overlayOptions.getShowConnections()) {
					Object connections = getImageData().getProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS);
					if (connections instanceof PathObjectConnections)
							PathHierarchyPaintingHelper.paintConnections((PathObjectConnections)connections, hierarchy, g2d, getImageData().isFluorescence() ? ColorToolsAwt.TRANSLUCENT_WHITE : ColorToolsAwt.TRANSLUCENT_BLACK, downsampleFactor);
				}
				
			} else {					
				// If the image hasn't been updated, then we are viewing the stationary image - we want to wait for a full repaint then to avoid flickering;
				// On the other hand, if a large image has been updated then we may be browsing quickly - better to repaint quickly while tiles may still be loading
				if (paintCompletely) {
////											System.out.println("Painting completely");
					regionStore.paintRegionCompletely(overlayServer, g2d, shapeRegion, z, t, downsampleFactor, observer, null, 5000);
				}
				else {
////											System.out.println("Painting PROGRESSIVELY");
					regionStore.paintRegion(overlayServer, g2d, shapeRegion, z, t, downsampleFactor, null, observer, null);
				}
			}
		}

		long endTime = System.currentTimeMillis();
		if (endTime - startTime > 500)
			logger.debug(String.format("Painting time: %.4f seconds", (endTime-startTime)/1000.));
		
		// Paint the annotations
		Collection<PathObject> pathObjects = hierarchy.getObjectsForRegion(PathAnnotationObject.class, region, null);

		Collection<PathObject> selectedObjects = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		selectedObjects.removeIf(p -> !p.hasROI() || (p.getROI().getZ() != z || p.getROI().getT() != t));
		
		pathObjects.removeAll(selectedObjects);

		List<PathObject> pathObjectList = new ArrayList<>(pathObjects);
		Collections.sort(pathObjectList, (p1, p2) -> {
			return Integer.compare(p1.getLevel(), p2.getLevel());
		});
		
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// The setting below stops some weird 'jiggling' effects during zooming in/out, or poor rendering of shape ROIs
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		// Ensure that selected objects are painted last, to make sure they aren't obscured
		List<PathObject> selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		if (!selected.isEmpty()) {
			PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, pathObjectList, overlayOptions, null, downsampleFactor);
			Composite previousComposite = g2d.getComposite();
			float opacity = overlayOptions.getOpacity();
			if (opacity < 1) {
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
				PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, selected, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);
				g2d.setComposite(previousComposite);
			} else {
				PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, selected, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);				
			}			
		} else
			PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, pathObjectList, overlayOptions, null, downsampleFactor);
	}


	
	public void clearCachedOverlay() {
		if (regionStore != null && overlayServer != null)
			regionStore.clearCacheForServer(overlayServer);
	}
	
	
	public void clearCachedOverlayForRegion(ImageRegion request) {
		if (regionStore != null && overlayServer != null)
			regionStore.clearCacheForRequestOverlap(RegionRequest.createInstance(overlayServer.getPath(), 1, request));
	}
	
	
	@Override
	public boolean supportsImageDataChange() {
		return true;
	}


	
	/**
	 * Comparator that makes use of levels, not only location.
	 *
	 */
	public static class DetectionComparator implements Comparator<PathObject> {
		
		private Comparator<PathObject> baseComparator = DefaultPathObjectComparator.getInstance();

		@Override
		public int compare(PathObject o1, PathObject o2) {
			int level = Integer.compare(o1.getLevel(), o2.getLevel());
			if (level == 0)
				return baseComparator.compare(o1, o2);
			return level;
		}
		
	}
	
	
}
