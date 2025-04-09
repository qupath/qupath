/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.images.servers.PathHierarchyImageServer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathObjectPainter;
import qupath.lib.images.ImageData;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.DefaultPathObjectConnectionGroup;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;


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

	// Map of points around which names should be displayed, to avoid frequent searches
	private Map<ROI, Point2> nameConnectionPointMap = Collections.synchronizedMap(new WeakHashMap<>());

	// Map of colors to use for displaying names, to avoid generating new color objects too often
	private Map<Integer, Color> nameColorMap = new ConcurrentHashMap<>();

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


	/**
	 * Reset the last image data.
	 * The hierarchy overlay stores the last-seen image data internally to assist with caching, but retaining a reference
	 * too long could become a memory leak.
	 */
	public void resetImageData() {
		this.imageData = null;
		updateOverlayServer();
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
		
		OverlayOptions overlayOptions = getOverlayOptions();
		long timestamp = overlayOptions.lastChangeTimestamp().get();
		int pointRadius = PathPrefs.pointRadiusProperty().get();
		if (overlayOptionsTimestamp != timestamp || pointRadius != lastPointRadius) {
			lastPointRadius = pointRadius;
			overlayOptionsTimestamp = timestamp;
		}
		
		int t = imageRegion.getT();
		int z = imageRegion.getZ();
		
		// Ensure antialias is on...?
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, defaultAntiAlias);
		
		// Get the displayed clip bounds for fast checking if ROIs need to be drawn
		Shape shapeRegion = g2d.getClip();
		if (shapeRegion == null) {
			shapeRegion = AwtTools.getBounds(imageRegion);
			g2d.setClip(shapeRegion);
		}
		var boundsDisplayed = shapeRegion.getBounds();
		
		// Note: the following was commented out for v0.4.0, because objects becoming invisible 
		// when outside the image turned out to be problematic more than helpful
		
		if (boundsDisplayed.width <= 0 || boundsDisplayed.height <= 0)
			return;

		// Determine the visible region
		ImageRegion region = AwtTools.getImageRegion(boundsDisplayed, z, t);

		// Get the annotations & selected objects (which must be painted directly)
		Set<PathObject> paintableSelectedObjects = new LinkedHashSet<>(hierarchy.getSelectionModel().getSelectedObjects());
		paintableSelectedObjects.removeIf(p -> p == null || !roiBoundsIntersectsRegion(p.getROI(), region));

		// Get all visible objects (including selected ones)
		boolean showAnnotations = overlayOptions.getShowAnnotations();
		boolean showDetections = overlayOptions.getShowDetections();
		// We only need detections if we're painting them directly - otherwise we use cached tiles instead.
		// This means that detection names don't display when zoomed out, but that's probably OK and possibly even desirable.
		// Without this, repainting with a large number of detections is made more sluggish by continually requesting
		// the detections from the hierarchy, even when viewing the image at a low resolution & the only purpose of
		// requesting them is to check if they have names (which is almost never the case).
		Collection<PathObject> paintableDetections = showDetections && downsampleFactor <= 1.0 ?
				hierarchy.getAllDetectionsForRegion(region) :
				Collections.emptyList();

		Collection<PathObject> paintableAnnotations = showAnnotations ?
				hierarchy.getAnnotationsForRegion(region) :
				Collections.emptyList();

		// Return if nothing visible
		if (!showDetections && paintableSelectedObjects.isEmpty() && paintableDetections.isEmpty() && paintableAnnotations.isEmpty())
			return;

		// Paint detection objects, if required
		long startTime = System.currentTimeMillis();
		if (overlayOptions.getShowDetections()) {
			// If we aren't downsampling by much, or we're upsampling, paint directly - making sure to paint the right number of times, and in the right order
			if (overlayServer == null || regionStore == null || !paintableDetections.isEmpty()) {
				Collection<PathObject> detectionsToPaint;
				try {
					detectionsToPaint = new TreeSet<>(comparator);
					detectionsToPaint.addAll(paintableDetections);
				} catch (IllegalArgumentException e) {
					// This can happen (rarely) in a multithreaded environment if the level of a detection changes.
					// However, protecting against this fully by caching the level with integer boxing/unboxing would be expensive.
					logger.debug("Exception requesting detections to paint: " + e.getLocalizedMessage(), e);
					detectionsToPaint = paintableDetections;
				}
				// Paint selected objects at the end
				detectionsToPaint.removeIf(paintableSelectedObjects::contains);
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
				PathObjectPainter.paintSpecifiedObjects(g2d, detectionsToPaint, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);

				if (overlayOptions.getShowConnections()) {
					// If we have connections from the legacy 'Delaunay cluster features 2D', show those
					Object connections = imageData.getProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS);
					if (connections instanceof PathObjectConnections conn)
						PathObjectPainter.paintConnections(conn,
								hierarchy,
								g2d,
								imageData.isFluorescence() ? ColorToolsAwt.TRANSLUCENT_WHITE : ColorToolsAwt.TRANSLUCENT_BLACK,
								downsampleFactor,
								imageRegion.getImagePlane());
					else {
						// If we have cells, show them
						// Otherwise, show any detections we have
						var subdiv = hierarchy.getCellSubdivision(imageRegion.getImagePlane());
						if (subdiv.isEmpty())
							subdiv = hierarchy.getDetectionSubdivision(imageRegion.getImagePlane());

						PathObjectPainter.paintConnections(
								subdiv,
								hierarchy,
								g2d,
								imageData.isFluorescence() ? ColorToolsAwt.TRANSLUCENT_WHITE : ColorToolsAwt.TRANSLUCENT_BLACK,
								downsampleFactor,
								imageRegion.getImagePlane()
						);
					}
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

		// Paint annotations, using a defined order based on hierarchy level and area
		var annotationsToPaint = paintableAnnotations.stream()
				.filter(p -> !paintableSelectedObjects.contains(p))
				.sorted(Comparator.comparingInt(PathObject::getLevel).reversed()
						.thenComparing((PathObject p) -> -p.getROI().getArea()))
				.toList();
		PathObjectPainter.paintSpecifiedObjects(g2d, annotationsToPaint, overlayOptions, null, downsampleFactor);
		
		// Ensure that selected objects are painted last, to make sure they aren't obscured
		if (!paintableSelectedObjects.isEmpty()) {
			Composite previousComposite = g2d.getComposite();
			float opacity = overlayOptions.getOpacity();
			if (opacity < 1) {
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
				PathObjectPainter.paintSpecifiedObjects(g2d, paintableSelectedObjects, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);
				g2d.setComposite(previousComposite);
			} else {
				PathObjectPainter.paintSpecifiedObjects(g2d, paintableSelectedObjects, overlayOptions, hierarchy.getSelectionModel(), downsampleFactor);
			}
		}
		
		// Paint labels
		if (overlayOptions.getShowNames()) {

			// Get all objects with names that we might need to paint
			Set<PathObject> objectsWithNames = new LinkedHashSet<>();
			paintableDetections.stream().filter(p -> p.getName() != null).forEach(objectsWithNames::add);
			paintableAnnotations.stream().filter(p -> p.getName() != null).forEach(objectsWithNames::add);
			paintableSelectedObjects.stream().filter(p -> p.getName() != null).forEach(objectsWithNames::add);

			var detectionDisplayMode = overlayOptions.getDetectionDisplayMode();

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
			double fontDownsample = Math.min(downsampleFactor, 16);
			float fontSize = (float)(requestedFontSize * fontDownsample);
			if (!GeneralTools.almostTheSame(font.getSize2D(), fontSize, 0.001))
				font = font.deriveFont(fontSize);
			
			g2d.setFont(font);
			var metrics = g2d.getFontMetrics(font);
			var rect = new Rectangle2D.Double();
			var connector = new Line2D.Double();
			g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			for (var namedObject : objectsWithNames) {
				var name = namedObject.getName();

				var roi = namedObject.getROI();
				if (namedObject.isCell()) {
					if (detectionDisplayMode == OverlayOptions.DetectionDisplayMode.NUCLEI_ONLY) {
						roi = PathObjectTools.getNucleusOrMainROI(namedObject);
					} else if (detectionDisplayMode == OverlayOptions.DetectionDisplayMode.CENTROIDS) {
						roi = PathObjectTools.getNucleusOrMainROI(namedObject);
						roi = ROIs.createPointsROI(roi.getCentroidX(), roi.getCentroidY(), roi.getImagePlane());
					}
				}
				
				if (name != null && !name.isBlank() && roi != null && !overlayOptions.isHidden(namedObject)) {

					var bounds = metrics.getStringBounds(name, g2d);

					// Find a point to connect to within the ROI
					Point2 point = nameConnectionPointMap.computeIfAbsent(roi, this::findNamePointForROI);

					double pad = 5.0 * fontDownsample;
					double x = point.getX() - bounds.getWidth()/2.0;
					double y = point.getY() - (bounds.getY() + metrics.getAscent() + pad*4);
					rect.setFrame(x+bounds.getX()-pad, y+bounds.getY()-pad, bounds.getWidth()+pad*2, bounds.getHeight()+pad*2);

					// Get the object color
					int objectColorInt;
					if (hierarchy.getSelectionModel().isSelected(namedObject) && PathPrefs.useSelectedColorProperty().get())
						objectColorInt = PathPrefs.colorSelectedObjectProperty().get();
					else
						objectColorInt = ColorToolsFX.getDisplayedColorARGB(namedObject).intValue();

					// Draw a line to where the name box will be
					var objectColor = ColorToolsAwt.getCachedColor(objectColorInt);
					float thickness = (float)(PathPrefs.annotationStrokeThicknessProperty().get() * fontDownsample);
					g2d.setColor(objectColor);
					g2d.setStroke(PathObjectPainter.getCachedStroke(thickness));
					connector.setLine(rect.getCenterX(), rect.getMaxY(), point.getX(), point.getY());
					g2d.draw(connector);

					// Draw a name box
					g2d.draw(rect);
					var colorTranslucent = nameColorMap.computeIfAbsent(objectColorInt, this::getNameRectangleColor);
					g2d.setColor(colorTranslucent);
					g2d.fill(rect);
					g2d.setColor(Color.WHITE);

					// Draw the text
					g2d.drawString(name, (float)x, (float)y);
				}
			}
		}
	}

	/**
	 * Quick test to see if an ROI's bounds intersect a specified region.
	 * @param roi
	 * @param region
	 * @return true if the roi is non-null and its bounds intersect the region, false otherwise
	 */
	private static boolean roiBoundsIntersectsRegion(ROI roi, ImageRegion region) {
		return roi != null && roi.getZ() == region.getZ() && roi.getT() == region.getT() &&
				region.intersects(
						roi.getBoundsX(),
						roi.getBoundsY(),
						Math.max(roi.getBoundsWidth(), 1e-3), // Handle points / lines with 0 width or height
						Math.max(roi.getBoundsHeight(), 1e-3));
	}


	/**
	 * Get a color to use to fill the bounding box when showing an object's name
	 * @param objectColorInt
	 * @return
	 */
	private Color getNameRectangleColor(Integer objectColorInt) {
		float darken = 0.6f;
		return ColorToolsAwt.getCachedColor(
				Math.round(ColorTools.red(objectColorInt) * darken),
				Math.round(ColorTools.green(objectColorInt) * darken),
				Math.round(ColorTools.blue(objectColorInt) * darken),
				128
		);
	}

	/**
	 * Find a point around which to display an object's name, if required.
	 * @param roi
	 * @return
	 */
	private Point2 findNamePointForROI(ROI roi) {
		if (roi instanceof RectangleROI || roi instanceof EllipseROI) {
			// Use top centre for rectangle and ellipses
			return new Point2(roi.getCentroidX(), roi.getBoundsY());
		} else if (roi instanceof LineROI) {
			// Use centroids for lines (2 points only)
			return new Point2(roi.getCentroidX(), roi.getCentroidY());
		} else {
			Point2 target = new Point2(roi.getCentroidX(), roi.getBoundsY());
			return roi.getAllPoints().stream()
					.filter(p -> Math.abs(p.getY() - target.getY()) < 1e-3)
					.min(Comparator.comparingDouble(p -> p.distanceSq(target)))
					.get();
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
