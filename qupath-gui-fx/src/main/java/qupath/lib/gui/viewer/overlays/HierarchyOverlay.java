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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.images.servers.PathHierarchyImageServer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.images.ImageData;
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
 * An overlay capable of painting a {@link PathObjectHierarchy}, <i>except</i> for any 
 * TMA grid (which is handled by {@link TMAGridOverlay}).
 * 
 * @author Pete Bankhead
 *
 */
public class HierarchyOverlay extends AbstractOverlay {
	
	private static final Logger logger = LoggerFactory.getLogger(HierarchyOverlay.class);

	private ImageData<BufferedImage> imageData;
	private PathHierarchyImageServer overlayServer = null;

	private DefaultImageRegionStore regionStore = null;
	
	private long overlayOptionsTimestamp;
	
	private int lastPointRadius = PathPrefs.pointRadiusProperty().get();
	
	private Font font = new Font("SansSerif", Font.BOLD, 10);
	
	/**
	 * Comparator to determine the order in which detections should be painted.
	 * This should be used with caution! Check out the docs for the class for details.
	 */
	private transient DetectionComparator comparator = new DetectionComparator();

	/**
	 * Constructor. Note that a {@link HierarchyOverlay} cannot adapt very efficient to changes in {@link ImageData}, and therefore 
	 * should not be reused across viewers.
	 * @param regionStore region store to cache image tiles
	 * @param overlayOptions overlay options to control display
	 * @param imageData current image data
	 */
	public HierarchyOverlay(final DefaultImageRegionStore regionStore, final OverlayOptions overlayOptions, final ImageData<BufferedImage> imageData) {
		super(overlayOptions);
		this.regionStore = regionStore;
		this.imageData = imageData;
		updateOverlayServer();
	}
	
	
	private void updateOverlayServer() {
		clearCachedOverlay();
		if (imageData == null)
			overlayServer = null;
		else {
			// If the image is small, don't really need a server at all...
			overlayServer = new PathHierarchyImageServer(imageData, getOverlayOptions());
		}
	}
	

	@Override
	public void paintOverlay(final Graphics2D g2d, final ImageRegion imageRegion, final double downsampleFactor, final ImageData<BufferedImage> imageData, final boolean paintCompletely) {
		
		if (this.imageData != imageData) {
			this.imageData = imageData;
			updateOverlayServer();
		}
		
		// Get the selection model, which can influence colours (TODO: this might not be the best way to do it!)
		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null)
			return;
		
		if (!isVisible() && hierarchy.getSelectionModel().noSelection())
			return;

		// Default RenderingHints (may be temporarily changed in some places)
		var defaultAntiAlias = RenderingHints.VALUE_ANTIALIAS_ON;
		var defaultStroke = RenderingHints.VALUE_STROKE_PURE;
		
		// Doesn't seem to help...?
//		boolean fastRendering = true;
//		if (fastRendering) {
//			defaultAntiAlias = RenderingHints.VALUE_ANTIALIAS_OFF;
//			defaultStroke = RenderingHints.VALUE_STROKE_DEFAULT;			
//		}


		OverlayOptions overlayOptions = getOverlayOptions();
		long timestamp = overlayOptions.lastChangeTimestamp().get();
		int pointRadius = PathPrefs.pointRadiusProperty().get();
		if (overlayOptionsTimestamp != timestamp || pointRadius != lastPointRadius) {
			lastPointRadius = pointRadius;
			overlayOptionsTimestamp = timestamp;
		}
		
		int t = imageRegion.getT();
		int z = imageRegion.getZ();
		
//		Rectangle serverBounds = AwtTools.getBounds(imageRegion);
		
		// Ensure antialias is on...?
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, defaultAntiAlias);
		
		// Get the displayed clip bounds for fast checking if ROIs need to be drawn
		Shape shapeRegion = g2d.getClip();
		if (shapeRegion == null)
			shapeRegion = AwtTools.getBounds(imageRegion);
		var boundsDisplayed = shapeRegion.getBounds();
		
		// Note: the following was commented out for v0.4.0, because objects becoming invisible 
		// when outside the image turned out to be problematic more than helpful
		
		// Ensure the bounds do not extend beyond what the server actually contains
//		boundsDisplayed = boundsDisplayed.intersection(serverBounds);
		
		if (boundsDisplayed.width <= 0 || boundsDisplayed.height <= 0)
			return;

		// Get the annotations & selected objects (which must be painted directly)
		Collection<PathObject> selectedObjects = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		selectedObjects.removeIf(p -> p == null || !p.hasROI() || (p.getROI().getZ() != z || p.getROI().getT() != t));

		ImageRegion region = AwtTools.getImageRegion(boundsDisplayed, z, t);
		
		// Paint detection objects	
		long startTime = System.currentTimeMillis();
		if (overlayOptions.getShowDetections() && !hierarchy.isEmpty()) {

			// If we aren't downsampling by much, or we're upsampling, paint directly - making sure to paint the right number of times, and in the right order
			if (overlayServer == null || regionStore == null || downsampleFactor < 1.0) {
				Collection<PathObject> pathObjects;
				try {
					Set<PathObject> pathObjectsToPaint = new TreeSet<>(comparator);					
					pathObjects = hierarchy.getObjectsForRegion(PathDetectionObject.class, region, pathObjectsToPaint);
				} catch (IllegalArgumentException e) {
					// This can happen (rarely) in a multithreaded environment if the level of a detection changes.
					// However, protecting against this fully by caching the level with integer boxing/unboxing would be expensive.
					logger.debug("Exception requesting detections to paint: " + e.getLocalizedMessage(), e);
					pathObjects = hierarchy.getObjectsForRegion(PathDetectionObject.class, region, null);
				}
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
				PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, pathObjects, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);
				
				if (overlayOptions.getShowConnections()) {
					Object connections = imageData.getProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS);
					if (connections instanceof PathObjectConnections)
							PathHierarchyPaintingHelper.paintConnections((PathObjectConnections)connections,
									hierarchy,
									g2d,
									imageData.isFluorescence() ? ColorToolsAwt.TRANSLUCENT_WHITE : ColorToolsAwt.TRANSLUCENT_BLACK,
											downsampleFactor,
											imageRegion.getImagePlane());
				}
				
			} else {					
				// If the image hasn't been updated, then we are viewing the stationary image - we want to wait for a full repaint then to avoid flickering;
				// On the other hand, if a large image has been updated then we may be browsing quickly - better to repaint quickly while tiles may still be loading
				if (paintCompletely) {
					regionStore.paintRegionCompletely(overlayServer, g2d, shapeRegion, z, t, downsampleFactor, null, null, 5000);
				}
				else {
					regionStore.paintRegion(overlayServer, g2d, shapeRegion, z, t, downsampleFactor, null, null, null);
				}
			}
		}

		long endTime = System.currentTimeMillis();
		if (endTime - startTime > 500)
			logger.debug("Painting time: {} seconds", GeneralTools.formatNumber((endTime-startTime)/1000.0, 4));

		// The setting below stops some weird 'jiggling' effects during zooming in/out, or poor rendering of shape ROIs
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, defaultAntiAlias);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, defaultStroke);

		// Prepare to handle labels, if we need to
		Collection<PathObject> objectsWithNames = new ArrayList<>();
		Collection<PathObject> annotations = hierarchy.getObjectsForRegion(PathAnnotationObject.class, region, null);
		for (var iterator = annotations.iterator(); iterator.hasNext(); ) {
			var next = iterator.next();
			if ((next.getName() != null && !next.getName().isBlank()))
				objectsWithNames.add(next);
			if (selectedObjects.contains(next))
				iterator.remove();
		}
		
		// Paint the annotations
		List<PathObject> pathObjectList = new ArrayList<>(annotations);
		Collections.sort(pathObjectList, Comparator.comparingInt(PathObject::getLevel).reversed()
				.thenComparing(Comparator.comparingDouble((PathObject p) -> -p.getROI().getArea())));
		PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, pathObjectList, overlayOptions, null, downsampleFactor);	
		
		// Ensure that selected objects are painted last, to make sure they aren't obscured
		if (!selectedObjects.isEmpty()) {
			Composite previousComposite = g2d.getComposite();
			float opacity = overlayOptions.getOpacity();
			if (opacity < 1) {
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
				PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, selectedObjects, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);
				g2d.setComposite(previousComposite);
			} else {
				PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, boundsDisplayed, selectedObjects, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);				
			}
		}
		
		// Paint labels
		if (overlayOptions.getShowNames() && !objectsWithNames.isEmpty()) {
			
			double requestedFontSize = overlayOptions.getFontSize();
			if (requestedFontSize <= 0 || !Double.isFinite(requestedFontSize)) {
				// Get it from the location font size instead
				switch (PathPrefs.locationFontSizeProperty().get()) {
				case HUGE:
					requestedFontSize = 24;
					break;
				case LARGE:
					requestedFontSize = 18;
					break;
				case SMALL:
					requestedFontSize = 10;
					break;
				case TINY:
					requestedFontSize = 8;
					break;
				case MEDIUM:
				default:
					requestedFontSize = 14;
					break;
				}
			}
			float fontSize = (float)(requestedFontSize * downsampleFactor);
			if (!GeneralTools.almostTheSame(font.getSize2D(), fontSize, 0.001))
				font = font.deriveFont(fontSize);
			
			g2d.setFont(font);
			var metrics = g2d.getFontMetrics(font);
			var rect = new Rectangle2D.Double();
			g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			for (var annotation : objectsWithNames) {
				var name = annotation.getName();
				
				var roi = annotation.getROI();
				
				if (name != null && !name.isBlank() && roi != null && !overlayOptions.isPathClassHidden(annotation.getPathClass())) {
					g2d.setColor(ColorToolsAwt.TRANSLUCENT_BLACK);
	
					var bounds = metrics.getStringBounds(name, g2d);
					
					double pad = 5.0 * downsampleFactor;
					double x = roi.getCentroidX() - bounds.getWidth() / 2.0;
					double y = roi.getCentroidY() + bounds.getY() + metrics.getAscent() + pad;
	
					rect.setFrame(x+bounds.getX()-pad, y+bounds.getY()-pad, bounds.getWidth()+pad*2, bounds.getHeight()+pad*2);
					g2d.fill(rect);
					g2d.setColor(Color.WHITE);
	
					g2d.drawString(name, (float)x, (float)y);
				}
			}
		}
		
	}
	
	/**
	 * Clear previously-cached tiles for this overlay.
	 */
	public void clearCachedOverlay() {
		if (regionStore != null && overlayServer != null)
			regionStore.clearCacheForServer(overlayServer);
	}
	
	/**
	 * Clear previously-cached tiles for a specified region of this overlay.
	 * @param region the region for which tiles should be removed
	 */
	public void clearCachedOverlayForRegion(ImageRegion region) {
		if (regionStore != null && overlayServer != null)
			regionStore.clearCacheForRequestOverlap(RegionRequest.createInstance(overlayServer.getPath(), 1, region));
	}

	
	/**
	 * Comparator that makes use of levels, not only location.
	 * <p>
	 * Warning! Because levels are mutable, this can fail if used for sorting while the object hierarchy is being modified
	 * in another thread.
	 */
	private static class DetectionComparator implements Comparator<PathObject> {
		
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
