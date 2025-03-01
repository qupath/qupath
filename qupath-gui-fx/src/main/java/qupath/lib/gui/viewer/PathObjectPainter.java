/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.DelaunayTools;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.LogTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.MeasurementMapper;
import qupath.lib.gui.viewer.OverlayOptions.DetectionDisplayMode;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnectionGroup;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.plugins.ParallelTileObject;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;


/**
 * Static methods to assist with painting PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectPainter {

	private static final Logger logger = LoggerFactory.getLogger(PathObjectPainter.class);

	private static final ShapeProvider shapeProvider = new ShapeProvider();

	private static final Map<Object, Double> doubleCache = new HashMap<>();

	private static final Map<Number, Stroke> strokeMap = new HashMap<>();
	private static final Map<Number, Stroke> dashedStrokeMap = new HashMap<>();

	private static final ThreadLocal<Path2D> localPath2D = ThreadLocal.withInitial(Path2D.Double::new);
	private static final ThreadLocal<Rectangle2D> localRect2D = ThreadLocal.withInitial(Rectangle2D.Double::new);
	private static final ThreadLocal<Ellipse2D> localEllipse2D = ThreadLocal.withInitial(Ellipse2D.Double::new);

	private PathObjectPainter() {}

	/**
	 * Paint the specified objects.
	 * 
	 * @param g2d the graphics object on which the objects should be painted
	 * @param pathObjects the objects to paint
	 * @param overlayOptions the overlay options defining how objects should be painted
	 * @param selectionModel the selection model used to determine the selection status of each object
	 * @param downsample the downsample factor; this should already be applied to the graphics object, but is needed to determine some line thicknesses
	 */
	public static void paintSpecifiedObjects(Graphics2D g2d, Collection<? extends PathObject> pathObjects, OverlayOptions overlayOptions, PathObjectSelectionModel selectionModel, double downsample) {
		// Paint objects, if required
		if (pathObjects == null)
			return;
		for (PathObject object : pathObjects) {
			if (Thread.currentThread().isInterrupted())
				return;
			paintObject(object, g2d, overlayOptions, selectionModel, downsample);
		}
	}


	/**
	 * Paint the specified tissue microarray grid.
	 * 
	 * @param g2d the graphics object on which the objects should be painted
	 * @param tmaGrid the TMA grid
	 * @param overlayOptions the overlay options defining how objects should be painted
	 * @param selectionModel the selection model used to determine the selection status of each object
	 * @param downsample the downsample factor; this should already be applied to the graphics object, but is needed to determine some line thicknesses
	 */
	public static void paintTMAGrid(Graphics2D g2d, TMAGrid tmaGrid, OverlayOptions overlayOptions, PathObjectSelectionModel selectionModel, double downsample) {
		if (tmaGrid == null)
			return;
		// Paint the TMA grid, if required
		if (!overlayOptions.getShowTMAGrid())
			return;
		Stroke strokeThick = getCachedStroke(PathPrefs.annotationStrokeThicknessProperty().get() * downsample);
		g2d.setStroke(strokeThick);
		// Paint the TMA grid
		Color colorGrid = ColorToolsAwt.getCachedColor(PathPrefs.colorTMAProperty().get());
		for (int gy = 0; gy < tmaGrid.getGridHeight(); gy++) {
			for (int gx = 0; gx < tmaGrid.getGridWidth(); gx++) {
				g2d.setStroke(strokeThick); // Reset stroke for lines
				TMACoreObject core = tmaGrid.getTMACore(gy, gx);
				Rectangle2D bounds = AwtTools.getBounds2D(core.getROI());
				g2d.setColor(colorGrid);
				if (gx < tmaGrid.getGridWidth() - 1) {
					Rectangle2D boundsNext = AwtTools.getBounds2D(tmaGrid.getTMACore(gy, gx+1).getROI());
					g2d.drawLine((int)bounds.getMaxX(), (int)bounds.getCenterY(), (int)boundsNext.getMinX(), (int)boundsNext.getCenterY());
				}
				if (gy < tmaGrid.getGridHeight() - 1) {
					Rectangle2D boundsNext = AwtTools.getBounds2D(tmaGrid.getTMACore(gy+1, gx).getROI());
					g2d.drawLine((int)bounds.getCenterX(), (int)bounds.getMaxY(), (int)boundsNext.getCenterX(), (int)boundsNext.getMinY());
				}
				PathObjectPainter.paintObject(core, g2d, overlayOptions, selectionModel, downsample);
			}
		}


		// Draw labels, if required
		if (overlayOptions.getShowTMACoreLabels()) {

			// Check if most of them fit - don't draw them if not
			int fitCount = 0;
			int totalCount = 0;
			FontMetrics fm = g2d.getFontMetrics();
			for (TMACoreObject core : tmaGrid.getTMACoreList()) {
				if (core.getName() == null)
					continue;
				int width = fm.stringWidth(core.getName());
				if (width < core.getROI().getBoundsWidth() / downsample)
					fitCount++;
				totalCount++;
			}
			if (fitCount == 0 || fitCount / (double)totalCount < 0.8)
				return;

			AffineTransform transform = g2d.getTransform();
			g2d.setTransform(new AffineTransform());
			Point2D pSource = new Point2D.Double();
			Point2D pDest = new Point2D.Double();
			g2d.setFont(g2d.getFont().deriveFont(Font.BOLD));
			for (TMACoreObject core : tmaGrid.getTMACoreList()) {
				if (core.getName() == null)
					continue;

				// Don't draw if it would be too large
				int width = fm.stringWidth(core.getName());

				double x = core.getROI().getBoundsX() + core.getROI().getBoundsWidth()/2;
				double y = core.getROI().getBoundsY() + core.getROI().getBoundsHeight()/2;
				pSource.setLocation(x, y);
				transform.transform(pSource, pDest);
				//			g2d.setColor(ColorToolsAwt.TRANSLUCENT_BLACK);
				float xf = (float)(pDest.getX() - width/2.0);
				float yf = (float)(pDest.getY() + fm.getDescent());

				Color colorPainted = ColorToolsAwt.getCachedColor(ColorToolsFX.getDisplayedColorARGB(core));
				double mean = (colorPainted.getRed() + colorPainted.getGreen() + colorPainted.getBlue()) / (255.0 * 3.0);
				if (mean > 0.5)
					g2d.setColor(ColorToolsAwt.TRANSLUCENT_BLACK);
				else
					g2d.setColor(Color.WHITE);

				g2d.drawString(core.getName(), xf, yf);
			}
		}

	}


	/**
	 * Paint an object (or, more precisely, its ROI).
	 * 
	 * This is subject to the OverlayOptions, and therefore may not actually end up painting anything
	 * (if the settings are such that objects of the class provided are not to be displayed)
	 * 
	 * @param pathObject
	 * @param g
	 * @param overlayOptions
	 * @param selectionModel
	 * @param downsample
	 * @return true if anything was painted, false otherwise
	 */
	public static boolean paintObject(PathObject pathObject, Graphics2D g, OverlayOptions overlayOptions, PathObjectSelectionModel selectionModel, double downsample) {
		if (pathObject == null)
			return false;

		ROI roi = pathObject.getROI();
		if (roi == null)
			return false;

		if (!roiIntersectsClipBounds(g, roi))
			return false;

		// Always paint the selected object (if it's within the field of view)
		boolean isSelected = (selectionModel != null && selectionModel.isSelected(pathObject));

		// Always paint selected objects, otherwise check if the object should be hidden
		if (!isSelected) {
			if ((overlayOptions.isHidden(pathObject) && !pathObject.isTMACore())
					|| isHiddenObjectType(pathObject, overlayOptions))
				return false;
		}

		Color color = getBaseObjectColor(pathObject, overlayOptions, isSelected);

		// Check if we have only one or two pixels to draw - if so, just fill a rectangle quickly
		if (pathObject.isDetection() && isRoiTinyAfterDownsampling(roi, downsample)) {
			fillRoiBounds(g, roi, color);
			return true;
		}

		// Determine stroke/fill colors
		Color colorFill = updateFillColorFromBase(pathObject, color, overlayOptions);
		Color colorStroke = updateStrokeColorFromBase(pathObject, color, overlayOptions);
		if (colorFill != null && colorFill.equals(colorStroke))
			colorStroke = ColorToolsAwt.darkenColor(color);
		Stroke stroke = colorStroke == null ? null : calculateStroke(pathObject, downsample, isSelected);

		if (colorFill != null && pathObject.hasChildObjects())
			colorFill = ColorToolsAwt.getColorWithOpacity(colorFill, 0.1);

		if (stroke != null)
			g.setStroke(stroke);
		boolean paintSymbols = shouldPaintRoiAsSymbol(pathObject, overlayOptions);
		if (paintSymbols) {
			Shape shape = getCentroidSymbol(pathObject);
			paintShape(shape, g, colorStroke, stroke, colorFill);
		} else if (pathObject.isCell()) {
			PathCellObject cell = (PathCellObject)pathObject;
			if (overlayOptions.getShowCellBoundaries())
				paintROI(roi, g, colorStroke, stroke, colorFill, downsample);
			if (overlayOptions.getShowCellNuclei()) {
				var nucleus = cell.getNucleusROI();
				if (nucleus != null)
					paintROI(cell.getNucleusROI(), g, colorStroke, stroke, colorFill, downsample);
			}
		} else {
			paintROI(roi, g, colorStroke, stroke, colorFill, downsample);
			String arrowhead = getArrowheadStringOrNull(pathObject, roi);
			if (arrowhead != null) {
				paintArrowheads(g, roi, arrowhead, colorStroke, null, downsample);
			}
		}
		return true;
	}
	
	
	private static String getArrowheadStringOrNull(PathObject pathObject, ROI roi) {
		// We currently only support annotations with straight line ROIs
		if (pathObject.isAnnotation() && roi.isLine() && roi instanceof LineROI)
			return pathObject.getMetadata().getOrDefault("arrowhead", null);
		else
			return null;
	}
	
	private static void paintArrowheads(Graphics2D g, ROI roi, String arrow, Color colorStroke, Stroke stroke, double downsample) {
		var points = roi.getAllPoints();
		var pointsToDraw = new ArrayList<Point2>();
		var angles = new ArrayList<Double>();
		if (arrow.contains("<")) {
			pointsToDraw.add(points.get(0));
			if (points.size() > 1)
				angles.add(Math.atan2(
						points.get(0).getY() - points.get(1).getY(),
						points.get(0).getX() - points.get(1).getX()) + Math.PI/2.0);
			else
				angles.add(0.0);
		}
		if (arrow.contains(">")) {
			pointsToDraw.add(points.get(points.size()-1));
			if (points.size() > 1)
				angles.add(Math.atan2(
						points.get(1).getY() - points.get(0).getY(),
						points.get(1).getX() - points.get(0).getX()) + Math.PI/2.0);
			else
				angles.add(0.0);
		}
		for (int i = 0; i < pointsToDraw.size(); i++) {
			var p = pointsToDraw.get(i);
			double angle = angles.get(i);
			double width = (float)(PathPrefs.annotationStrokeThicknessProperty().get() * downsample * 6);
			double length = Math.min(width, roi.getLength()/3.0);
			double x = p.getX();
			double y = p.getY();
			var triangle = localPath2D.get();
			triangle.reset();
			triangle.moveTo(x, y);
			triangle.lineTo(x-width/2.0, y+length);
			triangle.lineTo(x+width/2.0, y+length);
			triangle.closePath();
			triangle.transform(AffineTransform.getRotateInstance(angle, p.getX(), p.getY()));
			paintShape(triangle, g, colorStroke, stroke, colorStroke);
		}
	}
	


	private static Color getBaseObjectColor(PathObject pathObject, OverlayOptions overlayOptions, boolean isSelected) {
		Color color = null;
		if (isSelected)
			color = getSelectedObjectColorOrNull();
		if (color != null)
			return color;

		MeasurementMapper mapper = pathObject.isDetection() ? getValidMeasurementMapperOrNull(overlayOptions) : null;
		if (mapper != null)
			return getColorFromMeasurementMapperOrNull(pathObject, mapper);
		Integer rgb = ColorToolsFX.getDisplayedColorARGB(pathObject);
		return ColorToolsAwt.getCachedColor(rgb);
	}


	private static boolean useDetectionStrokeWidth(double downsample) {
		return downsample >= 1 || !PathPrefs.newDetectionRenderingProperty().get();
	}


	private static Stroke calculateStroke(PathObject pathObject, double downsample, boolean isSelected) {
		if (pathObject.isDetection() && useDetectionStrokeWidth(downsample)) {
			// Detections inside detections get half the line width
			if (pathObject.getParent() instanceof PathDetectionObject)
				return getCachedStroke(PathPrefs.detectionStrokeThicknessProperty().get() / 2.0);
			else
				return getCachedStroke(PathPrefs.detectionStrokeThicknessProperty().get());
		} else {
			double thicknessScale = downsample * (isSelected && !PathPrefs.useSelectedColorProperty().get() ? 1.6 : 1);
			float thickness = (float)(PathPrefs.annotationStrokeThicknessProperty().get() * thicknessScale);
			if (isSelected && pathObject.getParent() == null && PathPrefs.selectionModeStatus().get()) {
				return getCachedStrokeDashed(thickness);
			} else {
				return getCachedStroke(thickness);
			}
		}
	}


	private static Color updateStrokeColorFromBase(PathObject pathObject, Color colorBase, OverlayOptions overlayOptions) {
		// Don't show tile boundaries when filling detections with an overlay color
		if (pathObject.isTile() && getValidMeasurementMapperOrNull(overlayOptions) != null && overlayOptions != null && overlayOptions.getFillDetections())
			return null;
		return colorBase;
	}


	private static Double tryToGetDouble(Object obj) {
		if (obj == null)
			return null;
		if (doubleCache.containsKey(obj))
			return doubleCache.getOrDefault(obj, null);
		return doubleCache.computeIfAbsent(obj, PathObjectPainter::tryToParseDouble);
	}

	private static Double tryToParseDouble(Object obj) {
		try {
			if (obj instanceof String) {
				return Double.parseDouble((String)obj);
			} else if (obj instanceof Number) {
				return ((Number)obj).doubleValue();
			}
		} catch (Exception e) {
			logger.warn("Unable to parse double from {}", obj);
		}		
		return null;
	}
	
	
	private static Double getFillOpacityFromMetadataOrNull(PathObject pathObject) {
		return tryToGetDouble(pathObject.getMetadata().getOrDefault("fillOpacity", null));
	}
	

	private static Color updateFillColorFromBase(PathObject pathObject, Color colorBase, OverlayOptions overlayOptions) {
		if (pathObject instanceof ParallelTileObject)
			return ColorToolsAwt.getMoreTranslucentColor(colorBase);

		var fillOpacity = getFillOpacityFromMetadataOrNull(pathObject);
		if (fillOpacity != null)
			return ColorToolsAwt.getColorWithOpacity(colorBase, fillOpacity);

		// Don't fill region class
		if (pathObject.getPathClass() == PathClass.StandardPathClasses.REGION)
			return null;

		if (pathObject.isDetection()) {
			if (!overlayOptions.getFillDetections())
				return null;
			if (pathObject.isCell() && overlayOptions.getShowCellBoundaries() && overlayOptions.getShowCellNuclei()) {
				return ColorToolsAwt.getMoreTranslucentColor(colorBase);
			} else if (pathObject.getParent() instanceof PathDetectionObject) {
				return ColorToolsAwt.getTranslucentColor(colorBase);
			} else if (pathObject.isTile() && pathObject.getPathClass() == null && overlayOptions.getMeasurementMapper() == null)
				// Show unclassified tiles with a very translucent fill
				return ColorToolsAwt.getMoreTranslucentColor(colorBase);
			else
				return colorBase;			
		}

		if (pathObject.isTMACore()) {
			return null;
		}

		if (pathObject.isAnnotation()) {
			if (pathObject.getROI().isPoint() && overlayOptions.getFillDetections())
				return colorBase;
			if (overlayOptions.getFillAnnotations())
				return ColorToolsAwt.getMoreTranslucentColor(colorBase);
			else
				return null;
		}

		return null;
	}


	private static MeasurementMapper getValidMeasurementMapperOrNull(OverlayOptions overlayOptions) {
		var mapper = overlayOptions.getMeasurementMapper();
		if (mapper == null || !mapper.isValid())
			return null;
		return mapper;
	}


	private static Color getColorFromMeasurementMapperOrNull(PathObject pathObject, MeasurementMapper mapper) {
		if (!pathObject.hasMeasurements())
			return null;
		Integer rgb = mapper.getColorForObject(pathObject);
		// If the mapper returns null, the object shouldn't be painted
		if (rgb == null)
			return null;
		return ColorToolsAwt.getCachedColor(rgb);
	}

	private static Color getSelectedObjectColorOrNull() {
		if (PathPrefs.useSelectedColorProperty().get()) {
			Integer rgb = PathPrefs.colorSelectedObjectProperty().getValue();
			if (rgb != null)
				return ColorToolsAwt.getCachedColor(rgb);
		}
		return null;
	}

	private static boolean isHiddenObjectType(PathObject pathObject, OverlayOptions overlayOptions) {
		if (pathObject.isAnnotation())
			return !overlayOptions.getShowAnnotations();
		if (pathObject.isDetection())
			return !overlayOptions.getShowDetections();
		if (pathObject.isTMACore())
			return !overlayOptions.getShowTMAGrid();
		return false;
	}


	private static void fillRoiBounds(Graphics2D g2d, ROI roi, Color color) {
		int x = (int)roi.getBoundsX();
		int y = (int)roi.getBoundsY();
		int w = (int)Math.ceil(roi.getBoundsWidth()); // Prefer rounding up, lest we lose a lot of regions unnecessarily
		int h = (int)Math.ceil(roi.getBoundsHeight());
		if (w > 0 && h > 0) {
			g2d.setColor(color);
			g2d.fillRect(x, y, w, h);
		}
	}


	private static boolean isRoiTinyAfterDownsampling(ROI roi, double downsample) {
		return downsample > 4 && roi.getBoundsWidth() / downsample < 3 && roi.getBoundsHeight() / downsample < 3;
	}


	private static boolean roiIntersectsClipBounds(Graphics2D g2d, ROI roi) {
		var bounds = g2d.getClipBounds();
		if (bounds == null)
			return true;
		return bounds.intersects(
				roi.getBoundsX(),
				roi.getBoundsY(),
				Math.max(1, roi.getBoundsWidth()),
				Math.max(1, roi.getBoundsHeight()));
	}


	private static int getNumSubclasses(PathClass pathClass) {
		if (pathClass == null)
			return 0;
		if (!pathClass.isDerivedClass())
			return 1;
		else
			return PathClassTools.splitNames(pathClass).size();
	}


	private static Shape getCentroidSymbol(PathObject pathObject) {
		ROI roi = PathObjectTools.getROI(pathObject, true);
		double radius = PathPrefs.detectionStrokeThicknessProperty().get() * 2.0;
		PathObject parent = pathObject.getParent();
		while (parent != null && parent.isDetection()) {
			radius /= 2.0;
			if (radius < 1) {
				radius = 1;
				break;
			}
			parent = parent.getParent();
		}
		int nSubclasses = getNumSubclasses(pathObject.getPathClass());
		return getCentroidSymbol(roi, nSubclasses, radius);
	}


	private static Shape getCentroidSymbol(ROI roi, int nSubclasses, double radius) {
		double x = roi.getCentroidX();
		double y = roi.getCentroidY();

		switch (nSubclasses) {
		case 0:
			var ellipse = localEllipse2D.get();
			ellipse.setFrame(x-radius, y-radius, radius*2, radius*2);								
			return ellipse;
		case 1:
			var rect = localRect2D.get();
			rect.setFrame(x-radius, y-radius, radius*2, radius*2);								
			return rect;
		case 2:
			var triangle = localPath2D.get();
			double sqrt3 = Math.sqrt(3.0);
			triangle.reset();
			triangle.moveTo(x, y-radius*2.0/sqrt3);
			triangle.lineTo(x-radius, y+radius/sqrt3);
			triangle.lineTo(x+radius, y+radius/sqrt3);
			triangle.closePath();
			return triangle;
		case 3:
			var plus = localPath2D.get();
			plus.reset();
			plus.moveTo(x, y-radius);
			plus.lineTo(x, y+radius);
			plus.moveTo(x-radius, y);
			plus.lineTo(x+radius, y);
			return plus;								
		default:
			var cross = localPath2D.get();
			cross.reset();
			radius /= Math.sqrt(2);
			cross.moveTo(x-radius, y-radius);
			cross.lineTo(x+radius, y+radius);
			cross.moveTo(x+radius, y-radius);
			cross.lineTo(x-radius, y+radius);
			return cross;
		}
	}


	private static boolean shouldPaintRoiAsSymbol(PathObject pathObject, OverlayOptions overlayOptions) {
		return overlayOptions.getDetectionDisplayMode() == DetectionDisplayMode.CENTROIDS && 
				pathObject.isDetection() && !pathObject.isTile();
	}



	private static void paintROI(ROI roi, Graphics2D g, Color colorStroke, Stroke stroke, Color colorFill, double downsample) {
		if (colorStroke == null && colorFill == null)
			return;

		Graphics2D g2d = (Graphics2D)g.create();
		if (RoiTools.isShapeROI(roi)) {
			Shape shape = shapeProvider.getShape(roi, downsample, g.getClipBounds());
			// Only pass the colorFill if we have an area (i.e. not a line/polyline)
			if (roi.isArea())
				paintShape(shape, g, colorStroke, stroke, colorFill);
			else if (roi.isLine())
				paintShape(shape, g, colorStroke, stroke, null);
		} else if (roi.isPoint()) {
			paintPoints(roi, g2d, PathPrefs.pointRadiusProperty().get(), colorStroke, stroke, colorFill, downsample);
		}
		g2d.dispose();
	}


	abstract static class ShapePool<T extends Shape> {

		private Map<Thread, T> map = new WeakHashMap<>();

		protected abstract T createShape();

		public T getShape() {
			Thread thread = Thread.currentThread();
			T shape = map.get(thread);
			if (shape == null) {
				shape = createShape();
				map.put(thread, shape);
			}
			return shape;
		}

	}

	static class RectanglePool extends ShapePool<Rectangle2D> {

		@Override
		protected Rectangle2D createShape() {
			return new Rectangle2D.Double();
		}

	}

	static class EllipsePool extends ShapePool<Ellipse2D> {

		@Override
		protected Ellipse2D createShape() {
			return new Ellipse2D.Double();
		}

	}

	static class LinePool extends ShapePool<Line2D> {

		@Override
		protected Line2D createShape() {
			return new Line2D.Double();
		}

	}

	/**
	 * 
	 * Convert PathShapes into Java AWT Shapes, reusing existing objects where possible in a thread-safe way.
	 * <p>
	 * The purpose is to facilitate painting, i.e. shapes are provided for use before being discarded.
	 * <p>
	 * It is essential that the calling code does not modify the shapes in any way, and it should also not return references
	 * to the shapes, as there is no guarantee they will remain in the same state whenever getShape is called again.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class ShapeProvider {

		static final int MIN_SIMPLIFY_VERTICES = 250;

		private final RectanglePool rectanglePool = new RectanglePool();
		private final EllipsePool ellipsePool = new EllipsePool();
		private final LinePool linePool = new LinePool();

		// Note: this relies upon the fact that the ROI is immutable shapes are cached
		private final Map<Area, GriddedArea> areaMap = Collections.synchronizedMap(new WeakHashMap<>());


		public Shape getShape(final ROI roi, final double downsample, final Rectangle clip) {
			var shape = getShape(roi, downsample);
			// Painting performance is poor for extremely complex shapes
			// Here, we try to get a cropped version of the shape when required
			if (shape instanceof Area area && clip != null) {
				var griddedArea = areaMap.computeIfAbsent(area, GriddedArea::new);
				shape = griddedArea.getArea(clip);
			}
			return shape;
		}

		private Shape getShape(final ROI roi, final double downsample) {
			if (roi instanceof RectangleROI) {
				Rectangle2D rectangle = rectanglePool.getShape();
				rectangle.setFrame(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
				return rectangle;
			}

			if (roi instanceof EllipseROI) {
				Ellipse2D ellipse = ellipsePool.getShape();
				ellipse.setFrame(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
				return ellipse;
			}

			if (roi instanceof LineROI l) {
				Line2D line = linePool.getShape();
                line.setLine(l.getX1(), l.getY1(), l.getX2(), l.getY2());
				return line;
			}

			return DownsampledShapeCache.getShapeForDownsample(roi, downsample);
		}

    }

	/**
	 * Helper class for working with <i>very</i> complex Areas (e.g. over 10_000 path segments).
	 * Painting these is very slow, so this class attempts to crop the area to smaller regions and
	 * maintain a cache of the cropped areas.
	 */
	private static class GriddedArea {

		private static int minSegments = 10_000;

		private static final Area EMPTY = new Area();

		private final Area mainArea;
		private final int nSegments;
		private final Rectangle mainClip;
		private final double mainClipArea;
		private Map<Rectangle, Area> cache;

		private GriddedArea(Area area) {
			this.mainArea = area;
			this.mainClip = area.getBounds();
			this.mainClipArea = boundsArea(mainClip);
			this.nSegments = countSegments(area);
		}

		private static int countSegments(Area area) {
			var iterator = area.getPathIterator(null);
			int nSegments = 0;
			while (!iterator.isDone()) {
				iterator.next();
				nSegments++;
			}
			return nSegments;
		}

		private void ensureCacheExists() {
			if (cache == null) {
				cache = Collections.synchronizedMap(new LinkedHashMap<>() {
					@Override
					protected boolean removeEldestEntry(Map.Entry<Rectangle, Area> eldest) {
						return size() > 200;
					}
				});
			}
		}

		public Area getArea(Rectangle clip) {
			if (clip.isEmpty())
				return EMPTY;
			if (nSegments < minSegments || clip == null || clip.contains(mainClip) || boundsArea(clip) > mainClipArea * 0.5)
				return mainArea;
			// Try to find a cached area that contains the clip
			ensureCacheExists();
			if (!cache.isEmpty()) {
				synchronized (cache) {
					for (var entry : cache.entrySet()) {
						if (entry.getKey().contains(clip))
							return entry.getValue();
					}
				}
			}
			// Create a crop, if needed
			var padded = createPaddedRectangle(clip, clip.width+1, clip.height+1);
			var cropped = crop(padded);
			cache.put(padded, cropped);
			return cropped;
		}

		private static double boundsArea(Rectangle rectangle) {
			return rectangle.getWidth() * rectangle.getHeight();
		}

		private static Rectangle createPaddedRectangle(Rectangle clip, int padX, int padY) {
			return new Rectangle(
					(int)(clip.getX()-padX),
					(int)(clip.getY()-padY),
					(int)(clip.getWidth()+padX*2),
					(int)(clip.getHeight()+padY*2)
					);
		}

		private Area crop(Rectangle clip) {
			if (clip.contains(mainClip))
				return mainArea;
			var croppedArea = new Area(clip);
			croppedArea.intersect(mainArea);
			return croppedArea;
		}


	}


	/**
	 * Paint the specified shape with specified stroke and fill colors.
	 * @param shape shape to paint
	 * @param g2d graphics object
	 * @param colorStroke stroke color (may be null)
	 * @param stroke stroke (may be null)
	 * @param colorFill fill color (may be null)
	 */
	public static void paintShape(Shape shape, Graphics2D g2d, Color colorStroke, Stroke stroke, Color colorFill) {
		long startTime = System.currentTimeMillis();
		if (colorFill != null) {
			g2d.setColor(colorFill);
			g2d.fill(shape); 
		}
		if (colorStroke != null) {
			if (stroke != null)
				g2d.setStroke(stroke);
			g2d.setColor(colorStroke);
			g2d.draw(shape);
		}
		long endTime = System.currentTimeMillis();
		if (logger.isTraceEnabled() && endTime - startTime > 1000) {
			logger.trace("Painting time: {} for shape={}", (endTime - startTime), shape);
		}
	}


	private static void paintPoints(ROI pathPoints, Graphics2D g2d, double radius, Color colorStroke, Stroke stroke, Color colorFill, double downsample) {
		PointsROI pathPointsROI = pathPoints instanceof PointsROI ? (PointsROI)pathPoints : null;
		if (pathPointsROI != null && PathPrefs.showPointHullsProperty().get()) {
			ROI convexHull = pathPointsROI.getConvexHull();
			if (convexHull != null) {
				Color colorHull = colorFill != null ? colorFill : colorStroke;
				colorHull = ColorToolsAwt.getColorWithOpacity(colorHull, 0.1);
				if (colorHull != null)
					paintShape(RoiTools.getShape(convexHull), g2d, null, null, colorHull);
			}
		}

		RectangularShape ellipse;

		//		double radius = pathPointsROI == null ? PointsROI.defaultPointRadiusProperty().get() : pathPointsROI.getPointRadius();
		// Ensure that points are drawn with at least a radius of one, after any transforms have been applied
		double scale = Math.max(1, downsample);
		radius = (Math.max(1 / scale, radius));

		// Get clip bounds
		Rectangle2D bounds = g2d.getClipBounds();
		if (bounds != null) {
			bounds.setRect(bounds.getX()-radius, bounds.getY()-radius, bounds.getWidth()+radius*2, bounds.getHeight()+radius*2);
		}
		// Don't fill if we have a small radius, and use a rectangle instead of an ellipse (as this repaints much faster)
		Graphics2D g = g2d;
		if (radius / downsample < 0.5) {
			if (colorStroke == null)
				colorStroke = colorFill;
			colorFill = null;
			ellipse = new Rectangle2D.Double();
			// Use opacity to avoid obscuring points completely
			int rule = AlphaComposite.SRC_OVER;
			float alpha = (float)(radius / downsample);
			var composite = g.getComposite();
			if (composite instanceof AlphaComposite) {
				var temp = (AlphaComposite)composite;
				rule = temp.getRule();
				alpha = temp.getAlpha() * alpha;
			}
			// If we are close to completely transparent, do not paint
			if (alpha < 0.01f)
				return;
			composite = AlphaComposite.getInstance(rule, alpha);
			g = (Graphics2D)g2d.create();
			g.setComposite(composite);
		} else
			ellipse = new Ellipse2D.Double();

		g.setStroke(stroke);
		for (Point2 p : pathPoints.getAllPoints()) {
			if (bounds != null && !bounds.contains(p.getX(), p.getY()))
				continue;
			ellipse.setFrame(p.getX()-radius, p.getY()-radius, radius*2, radius*2);
			if (colorFill != null) {
				g.setColor(colorFill);
				g.fill(ellipse);
			}
			if (colorStroke != null) {
				g.setColor(colorStroke);
				g.draw(ellipse);
			}
		}
		if (g != g2d)
			g.dispose();
	}


	static Stroke getCachedStrokeDashed(final Number thickness) {
		Stroke stroke = dashedStrokeMap.get(thickness);
		if (stroke == null) {
			stroke = new BasicStroke(
					thickness.floatValue(),
					BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {thickness.floatValue() * 5f}, 0f);
			dashedStrokeMap.put(thickness, stroke);
		}
		return stroke;
	}

	static Stroke getCachedStroke(final Number thickness) {
		Stroke stroke = strokeMap.get(thickness);
		if (stroke == null) {
			stroke = new BasicStroke(thickness.floatValue());
			strokeMap.put(thickness, stroke);
		}
		return stroke;
	}

	private static Stroke getCachedStroke(final int thickness) {
		return getCachedStroke(Integer.valueOf(thickness));
	}

	/**
	 * Get a {@link BasicStroke} with the specified thickness.
	 * @param thickness
	 * @return
	 */
	public static Stroke getCachedStroke(final double thickness) {
		if (thickness == Math.rint(thickness))
			return getCachedStroke((int)thickness);
		return getCachedStroke(Float.valueOf((float)thickness));
	}


	/**
	 * Paint the handles onto a Graphics object, if we have a suitable (non-point) ROI.
	 * <p>
	 * The minimum and maximum handle size can be specified; if the same, all handles will have the same size.  If different, 
	 * then the distance between consecutive handles will be used to influence the actual handle size.  This is helpful 
	 * when handles are densely packed.
	 * 
	 * @param roiEditor 
	 * @param g2d
	 * @param minHandleSize
	 * @param maxHandleSize
	 * @param colorStroke
	 * @param colorFill
	 */
	public static void paintHandles(final RoiEditor roiEditor, final Graphics2D g2d, final double minHandleSize, final double maxHandleSize, final Color colorStroke, final Color colorFill) {		
		if (!(roiEditor.getROI() instanceof PointsROI))
			paintHandles(roiEditor.getHandles(), g2d, minHandleSize, maxHandleSize, colorStroke, colorFill);
	}

	/**
	 * Paint the handles onto a Graphics object, if we have a suitable (non-point) ROI.
	 * <p>
	 * The minimum and maximum handle size can be specified; if the same, all handles will have the same size.  If different, 
	 * then the distance between consecutive handles will be used to influence the actual handle size.  This is helpful 
	 * when handles are densely packed.
	 * 
	 * @param handles
	 * @param g2d
	 * @param minHandleSize
	 * @param maxHandleSize
	 * @param colorStroke
	 * @param colorFill
	 */
	public static void paintHandles(final List<Point2> handles, final Graphics2D g2d, final double minHandleSize, final double maxHandleSize, final Color colorStroke, final Color colorFill) {		
		RectangularShape handleShape = new Rectangle2D.Double();

		int n = handles.size();
		if (minHandleSize == maxHandleSize) {
			for (Point2 p : handles) {
				double handleSize = minHandleSize;
				handleShape.setFrame(p.getX()-handleSize/2, p.getY()-handleSize/2, handleSize, handleSize);
				if (colorFill != null) {
					g2d.setColor(colorFill);
					g2d.fill(handleShape);
				}
				if (colorStroke != null) {
					g2d.setColor(colorStroke);
					g2d.draw(handleShape);
				}
			}
			return;
		} else {
			for (int i = 0; i < handles.size(); i++) {
				var current = handles.get(i);
				var before = handles.get((i + n - 1) % n);
				var after = handles.get((i + 1) % n);
				double distance = Math.sqrt(Math.min(current.distanceSq(before), current.distanceSq(after))) * 0.5;
				double size = Math.max(minHandleSize, Math.min(distance, maxHandleSize));

				var p = current;
				handleShape.setFrame(p.getX()-size/2, p.getY()-size/2, size, size);
				if (colorFill != null) {
					g2d.setColor(colorFill);
					g2d.fill(handleShape);
				}
				if (colorStroke != null) {
					g2d.setColor(colorStroke);
					g2d.draw(handleShape);
				}
			}
		}
	}

	/**
	 * Return the stroke thickness to use for drawing connection lines between objects.
	 * @param downsample
	 * @return
	 */
	private static double getConnectionStrokeThickness(double downsample) {
		double thickness = PathPrefs.detectionStrokeThicknessProperty().get();
		// Don't try to draw connections if the line is too thin
		if (thickness / downsample <= 0.25)
			return 0;
		// Check if we're using the 'standard' stroke width, or the experimental new rendering
		if (useDetectionStrokeWidth(downsample))
			return thickness;
		else
			return thickness * Math.min(1, downsample);
	}

	/**
	 * Adjust the opacity of connection lines according to the downsample (since rendering a huge number
	 * is slow, and makes the image look cluttered).
	 * @param downsample
	 * @return
	 */
	private static float getConnectionAlpha(double downsample) {
		float alpha = (float)(1f - downsample / 5);
		return Math.min(alpha, 0.4f);
	}

	/**
	 * Paint connections between objects (e.g. from Delaunay triangulation).
	 * 
	 * @param connections
	 * @param hierarchy
	 * @param g2d
	 * @param color
	 * @param downsampleFactor
	 * @param plane
	 * @deprecated v0.6.0 as #paintConnections(DelaunayTools.Subdivision, PathObjectHierarchy, Graphics2D, Color, double, ImagePlane) is preferred
	 */
	@Deprecated
	public static void paintConnections(final PathObjectConnections connections, final PathObjectHierarchy hierarchy, Graphics2D g2d, final Color color, final double downsampleFactor, final ImagePlane plane) {
		if (hierarchy == null || connections == null || connections.isEmpty())
			return;

		LogTools.warnOnce(logger, "Legacy 'Delaunay cluster features 2D' connections are being shown in the viewer - this command is deprecated, and support will be removed in a future version");

		float alpha = getConnectionAlpha(downsampleFactor);
		double thickness = getConnectionStrokeThickness(downsampleFactor);
		if (alpha < .1f || thickness <= 0.0)
			return;

		g2d = (Graphics2D)g2d.create();

		g2d.setStroke(getCachedStroke(thickness));

		g2d.setColor(ColorToolsAwt.getColorWithOpacity(color.getRGB(), alpha));

		// We only need to draw connections that intersect with the bounds
		Rectangle bounds = g2d.getClipBounds();

		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);


		// We need all the detections since *potentially* both ends might be outside the visible bounds, 
		// but their connecting line intersects the bounds.
		// However, this can be a *major* performance issue (until a spatial cache is used), so instead we expand the bounds 
		// and hope that's enough to get all the objects we need.
		ImageRegion imageRegion = AwtTools.getImageRegion(bounds, plane.getZ(), plane.getT());

		// Keep reference to visited objects, to avoid painting the same line twice
		// (which happened in v0.3.2 and earlier)
		Set<PathObject> vistedObjects = new HashSet<>();

		// Reuse the line and record counts
		Line2D line = new Line2D.Double();
		int nDrawn = 0;
		int nSkipped = 0;

		long startTime = System.currentTimeMillis();
		for (PathObjectConnectionGroup dt : connections.getConnectionGroups()) {
			vistedObjects.clear();
			for (var pathObject : dt.getPathObjectsForRegion(imageRegion)) {

				vistedObjects.add(pathObject);
				ROI roi = PathObjectTools.getROI(pathObject, true);
				double x1 = roi.getCentroidX();
				double y1 = roi.getCentroidY();
				for (PathObject siblingObject : dt.getConnectedObjects(pathObject)) {
					if (vistedObjects.contains(siblingObject))
						continue;
					ROI roi2 = PathObjectTools.getROI(siblingObject, true);
					double x2 = roi2.getCentroidX();
					double y2 = roi2.getCentroidY();
					if (bounds.intersectsLine(x1, y1, x2, y2)) {
						line.setLine(x1, y1, x2, y2);
						g2d.draw(line);
						// Doesn't seem to be more efficient
						// g2d.drawLine((int)x1, (int)y1, (int)x2, (int)y2);
						nDrawn++;
					} else {
						nSkipped++;
					}
				}

			}
		}
		long endTime = System.currentTimeMillis();
		logger.trace("Drawn {} connections in {} ms ({} skipped)", nDrawn, endTime - startTime, nSkipped);
		g2d.dispose();
	}


	/**
	 * Paint connections between objects from a {@link qupath.lib.analysis.DelaunayTools.Subdivision}.
	 *
	 * @param subdivision
	 * @param hierarchy
	 * @param g2d
	 * @param color
	 * @param downsampleFactor
	 * @param plane
	 */
	public static void paintConnections(final DelaunayTools.Subdivision subdivision, final PathObjectHierarchy hierarchy, Graphics2D g2d, final Color color, final double downsampleFactor, final ImagePlane plane) {
		if (hierarchy == null || subdivision.size() <= 1)
			return;

		float alpha = getConnectionAlpha(downsampleFactor);
		double thickness = getConnectionStrokeThickness(downsampleFactor);
		if (alpha < .1f || thickness <= 0.0)
			return;

		g2d = (Graphics2D)g2d.create();

		g2d.setStroke(getCachedStroke(thickness));

		g2d.setColor(ColorToolsAwt.getColorWithOpacity(color.getRGB(), alpha));

		// We only need to draw connections that intersect with the bounds
		Rectangle bounds = g2d.getClipBounds();
		ImageRegion region = ImageRegion.createInstance(bounds.x, bounds.y, bounds.width, bounds.height, plane.getZ(), plane.getT());

		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

		// Keep reference to visited objects, to avoid painting the same line twice
		Set<PathObject> vistedObjects = new HashSet<>();

		// Reuse the line and record counts
		Line2D line = new Line2D.Double();
		int nDrawn = 0;
		int nSkipped = 0;

		long startTime = System.currentTimeMillis();
		for (var pathObject : subdivision.getObjectsForRegion(region)) {
			vistedObjects.add(pathObject);
			ROI roi = PathObjectTools.getROI(pathObject, true);
			double x1 = roi.getCentroidX();
			double y1 = roi.getCentroidY();
			for (var neighbor : subdivision.getNeighbors(pathObject)) {
				if (vistedObjects.contains(neighbor))
					continue;
				ROI roi2 = PathObjectTools.getROI(neighbor, true);
				double x2 = roi2.getCentroidX();
				double y2 = roi2.getCentroidY();
				if (bounds.intersectsLine(x1, y1, x2, y2)) {
					line.setLine(x1, y1, x2, y2);
					g2d.draw(line);
					nDrawn++;
				} else {
					nSkipped++;
				}
			}
		}
		long endTime = System.currentTimeMillis();
		logger.trace("Drawn {} connections in {} ms ({} skipped)", nDrawn, endTime - startTime, nSkipped);
		g2d.dispose();
	}


}
