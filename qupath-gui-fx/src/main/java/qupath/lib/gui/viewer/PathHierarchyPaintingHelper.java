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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
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
import qupath.lib.objects.PathTileObject;
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
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;


/**
 * Static methods to assist with painting PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
public class PathHierarchyPaintingHelper {
	
	private static final Logger logger = LoggerFactory.getLogger(PathHierarchyPaintingHelper.class);

	private static ShapeProvider shapeProvider = new ShapeProvider();
	
	private static Map<Number, Stroke> strokeMap = new HashMap<>();
	private static Map<Number, Stroke> dashedStrokeMap = new HashMap<>();
	
	private PathHierarchyPaintingHelper() {}
	
	/**
	 * Paint the specified objects.
	 * 
	 * @param g2d the graphics object on which the objects should be painted
	 * @param boundsDisplayed the displayable bounds, which can be used to filter out objects beyond the visible region
	 * @param pathObjects the objects to paint
	 * @param overlayOptions the overlay options defining how objects should be painted
	 * @param selectionModel the selection model used to determine the selection status of each object
	 * @param downsample the downsample factor; this should already be applied to the graphics object, but is needed to determine some line thicknesses
	 */
	public static void paintSpecifiedObjects(Graphics2D g2d, Rectangle boundsDisplayed, Collection<? extends PathObject> pathObjects, OverlayOptions overlayOptions, PathObjectSelectionModel selectionModel, double downsample) {
		//		System.out.println("MY CLIP: " + g2d.getClipBounds());
		// Paint objects, if required
		if (pathObjects == null)
			return;
		for (PathObject object : pathObjects) {
			if (Thread.currentThread().isInterrupted())
				return;
			paintObject(object, false, g2d, boundsDisplayed, overlayOptions, selectionModel, downsample);
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
		Rectangle boundsDisplayed = g2d.getClipBounds();
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
				PathHierarchyPaintingHelper.paintObject(core, false, g2d, boundsDisplayed, overlayOptions, selectionModel, downsample);
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
	
	private static ThreadLocal<Path2D> localPath2D = ThreadLocal.withInitial(Path2D.Double::new);
	private static ThreadLocal<Rectangle2D> localRect2D = ThreadLocal.withInitial(Rectangle2D.Double::new);
	private static ThreadLocal<Ellipse2D> localEllipse2D = ThreadLocal.withInitial(Ellipse2D.Double::new);
	
	/**
	 * Paint an object (or, more precisely, its ROI), optionally along with the ROIs of any child objects.
	 * 
	 * This is subject to the OverlayOptions, and therefore may not actually end up painting anything
	 * (if the settings are such that objects of the class provided are not to be displayed)
	 * 
	 * @param pathObject
	 * @param paintChildren
	 * @param g
	 * @param boundsDisplayed
	 * @param overlayOptions
	 * @param selectionModel
	 * @param downsample
	 * @return true if anything was painted, false otherwise
	 */
	public static boolean paintObject(PathObject pathObject, boolean paintChildren, Graphics2D g, Rectangle boundsDisplayed, OverlayOptions overlayOptions, PathObjectSelectionModel selectionModel, double downsample) {
		if (pathObject == null)
			return false;
				
//		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
//		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		
		// Always paint the selected object
		// Note: this makes the assumption that child ROIs are completely contained within their parents;
		//			this probably should be the case, but isn't guaranteed
		boolean isSelected = (selectionModel != null && selectionModel.isSelected(pathObject)) && (PathPrefs.useSelectedColorProperty().get() || !PathObjectTools.hasPointROI(pathObject));
		boolean isDetectedObject = pathObject.isDetection() || (pathObject.isTile() && pathObject.hasMeasurements());
		
		// Check if the PathClass isn't being shown
		PathClass pathClass = pathObject.getPathClass();
		if (!isSelected && overlayOptions != null && overlayOptions.isPathClassHidden(pathClass))
			return false;
		
		boolean painted = false;
		
		// See if we need to check the children
		ROI pathROI = pathObject.getROI();
		if (pathROI != null) {
			double roiBoundsX = pathROI.getBoundsX();
			double roiBoundsY = pathROI.getBoundsY();
			double roiBoundsWidth = pathROI.getBoundsWidth();
			double roiBoundsHeight = pathROI.getBoundsHeight();
			if (PathObjectTools.hasPointROI(pathObject) || boundsDisplayed == null || 
					pathROI instanceof LineROI || 
					boundsDisplayed.intersects(roiBoundsX, roiBoundsY, Math.max(roiBoundsWidth, 1), Math.max(roiBoundsHeight, 1))) {
			
				// Paint the ROI, if necessary
				if (isSelected || (overlayOptions.getShowDetections() && isDetectedObject) || (overlayOptions.getShowAnnotations() && pathObject.isAnnotation()) || (overlayOptions.getShowTMAGrid() && pathObject.isTMACore())) {
		
					boolean doFill = overlayOptions.getFillDetections() || pathObject instanceof ParallelTileObject;
					boolean doOutline = true;
					
					Color color = null;
					boolean useMapper = false;
					double fillOpacity = .75;
					if (isSelected && PathPrefs.useSelectedColorProperty().get() && PathPrefs.colorSelectedObjectProperty().getValue() != null)
						color = ColorToolsAwt.getCachedColor(PathPrefs.colorSelectedObjectProperty().get());
					else {
						MeasurementMapper mapper = overlayOptions.getMeasurementMapper();
						useMapper = mapper != null && mapper.isValid() && pathObject.isDetection();
						if (useMapper) {
							if (pathObject.hasMeasurements()) {
								Integer rgb = mapper.getColorForObject(pathObject);
								// If the mapper returns null, the object shouldn't be painted
								if (rgb == null)
									return false;
								color = ColorToolsAwt.getCachedColor(rgb);//, mapper.getColorMapper().hasAlpha());
							}
							else
								color = null;
	//						System.out.println(color + " - " + pathObject.getMeasurementList().getMeasurementValue(mapper.));
							fillOpacity = 1.0;
							// Outlines are not so helpful with the measurement mapper
							if (doFill)
								doOutline = doOutline && !pathObject.isTile();
						}
						else {
							Integer rgb = ColorToolsFX.getDisplayedColorARGB(pathObject);
							color = ColorToolsAwt.getCachedColor(rgb);
						}
//							color = PathObjectHelpers.getDisplayedColor(pathObject);
					}
					
					
					// Check if we have only one or two pixels to draw - if so, we can be done quickly
					if (isDetectedObject && downsample > 4 && roiBoundsWidth / downsample < 3 && roiBoundsHeight / downsample < 3) {
						int x = (int)roiBoundsX;
						int y = (int)roiBoundsY;
						int w = (int)(roiBoundsWidth + .9); // Prefer rounding up, lest we lose a lot of regions unnecessarily
						int h = (int)(roiBoundsHeight + .9);
						if (w > 0 && h > 0) {
							g.setColor(color);
//							g.setColor(DisplayHelpers.getMoreTranslucentColor(color));
//							g.setStroke(getCachedStroke(overlayOptions.strokeThinThicknessProperty().get()));
							g.fillRect(x, y, w, h);
						}
						painted = true;
					} else {
						Stroke stroke = null;
						// Decide whether to fill or not
						Color colorFill = doFill && (isDetectedObject || PathObjectTools.hasPointROI(pathObject)) ? color : null;
						if (colorFill != null && fillOpacity != 1) {
							if (pathObject instanceof ParallelTileObject)
								colorFill = ColorToolsAwt.getMoreTranslucentColor(colorFill);
							else if (pathObject instanceof PathCellObject && overlayOptions.getShowCellBoundaries() && overlayOptions.getShowCellNuclei()) {
//								if (isSelected)
//									colorFill = ColorToolsAwt.getTranslucentColor(colorFill);
//								else
									colorFill = ColorToolsAwt.getMoreTranslucentColor(colorFill);
							} else if (pathObject.getParent() instanceof PathDetectionObject) {
								colorFill = ColorToolsAwt.getTranslucentColor(colorFill);
							} else if (pathObject instanceof PathTileObject && pathClass == null && color !=null && color.getRGB() == PathPrefs.colorTileProperty().get()) {
								// Don't fill in empty, unclassified tiles
								colorFill = null; //DisplayHelpers.getMoreTranslucentColor(colorFill);
							}
						}
//						Color colorStroke = doOutline ? (colorFill == null ? color : (downsample > overlayOptions.strokeThinThicknessProperty().get() ? null : DisplayHelpers.darkenColor(color))) : null;
						Color colorStroke = doOutline ? (colorFill == null ? color : ColorToolsAwt.darkenColor(color)) : null;
						
						// For thick lines, antialiasing is very noticeable... less so for thin lines (of which there may be a huge number)
						if (isDetectedObject) {
							// Detections inside detections get half the line width
							if (pathObject.getParent() instanceof PathDetectionObject)
								stroke = getCachedStroke(PathPrefs.detectionStrokeThicknessProperty().get() / 2.0);
							else
								stroke = getCachedStroke(PathPrefs.detectionStrokeThicknessProperty().get());
						}
						else {
							double thicknessScale = downsample * (isSelected && !PathPrefs.useSelectedColorProperty().get() ? 1.6 : 1);
							float thickness = (float)(PathPrefs.annotationStrokeThicknessProperty().get() * thicknessScale);
							if (isSelected && pathObject.getParent() == null && PathPrefs.selectionModeProperty().get()) {
								stroke = getCachedStrokeDashed(thickness);
							} else {
								stroke = getCachedStroke(thickness);
							}
						}
						
						g.setStroke(stroke);
						boolean paintSymbols = overlayOptions.getDetectionDisplayMode() == DetectionDisplayMode.CENTROIDS && 
								pathObject.isDetection() && !pathObject.isTile();
						if (paintSymbols) {
							pathROI = PathObjectTools.getROI(pathObject, true);
							double x = pathROI.getCentroidX();
							double y = pathROI.getCentroidY();
							double radius = PathPrefs.detectionStrokeThicknessProperty().get() * 2.0;
							if (pathObject.getParent() instanceof PathDetectionObject)
								radius /= 2.0;
							Shape shape;
							int nSubclasses = 0;
							if (pathClass != null) {
								nSubclasses = PathClassTools.splitNames(pathClass).size();
							}
							switch (nSubclasses) {
							case 0:
								var ellipse = localEllipse2D.get();
								ellipse.setFrame(x-radius, y-radius, radius*2, radius*2);								
								shape = ellipse;
								break;
							case 1:
								var rect = localRect2D.get();
								rect.setFrame(x-radius, y-radius, radius*2, radius*2);								
								shape = rect;
								break;
							case 2:
								var triangle = localPath2D.get();
								double sqrt3 = Math.sqrt(3.0);
								triangle.reset();
								triangle.moveTo(x, y-radius*2.0/sqrt3);
								triangle.lineTo(x-radius, y+radius/sqrt3);
								triangle.lineTo(x+radius, y+radius/sqrt3);
								triangle.closePath();
								shape = triangle;
								break;
							case 3:
								var plus = localPath2D.get();
								plus.reset();
								plus.moveTo(x, y-radius);
								plus.lineTo(x, y+radius);
								plus.moveTo(x-radius, y);
								plus.lineTo(x+radius, y);
								shape = plus;								
								break;
							default:
								var cross = localPath2D.get();
								cross.reset();
								radius /= Math.sqrt(2);
								cross.moveTo(x-radius, y-radius);
								cross.lineTo(x+radius, y+radius);
								cross.moveTo(x+radius, y-radius);
								cross.lineTo(x-radius, y+radius);
								shape = cross;
								break;
							}
							paintShape(shape, g, colorStroke, stroke, colorFill);
						} else if (pathObject instanceof PathCellObject) {
							PathCellObject cell = (PathCellObject)pathObject;
							if (overlayOptions.getShowCellBoundaries())
								paintROI(pathROI, g, colorStroke, stroke, colorFill, downsample);
							if (overlayOptions.getShowCellNuclei())
								paintROI(cell.getNucleusROI(), g, colorStroke, stroke, colorFill, downsample);
							painted = true;
						} else {
							if ((overlayOptions.getFillAnnotations() &&
									pathObject.isAnnotation() && 
									pathObject.getPathClass() != PathClass.StandardPathClasses.REGION &&
									(pathObject.getPathClass() != null || !pathObject.hasChildObjects()))
									|| (pathObject.isTMACore() && overlayOptions.getShowTMACoreLabels()))
								paintROI(pathROI, g, colorStroke, stroke, ColorToolsAwt.getMoreTranslucentColor(colorStroke), downsample);
							else
								paintROI(pathROI, g, colorStroke, stroke, colorFill, downsample);
							painted = true;
						}
					}
				}
			}
		}
		// Paint the children, if necessary
		if (paintChildren) {
			for (PathObject childObject : pathObject.getChildObjectsAsArray()) {
				// Only call the painting method if required
				ROI childROI = childObject.getROI();
				if ((childROI != null && boundsDisplayed.intersects(childROI.getBoundsX(), childROI.getBoundsY(), childROI.getBoundsWidth(), childROI.getBoundsHeight())) || childObject.hasChildObjects())
					painted = paintObject(childObject, paintChildren, g, boundsDisplayed, overlayOptions, selectionModel, downsample) | painted;
			}
		}
		return painted;
	}
	
	

	private static void paintROI(ROI pathROI, Graphics2D g, Color colorStroke, Stroke stroke, Color colorFill, double downsample) {
//		if (pathROI == null || (pathROI.isEmpty() && !(pathROI instanceof PolygonROI)))
		// Reinstate drawing empty ROIs (removed in v0.4.0)
		// Need to paint empty polygons, since otherwise they don't appear when starting to draw
		// Potentially, all empty ROIs should be drawn
		if (pathROI == null)
			return;
//		pathROI.draw(g, colorStroke, colorFill);
		
		if (colorStroke == null && colorFill == null)
			return;
		
		Graphics2D g2d = (Graphics2D)g.create();
		if (RoiTools.isShapeROI(pathROI)) {
//			Shape shape = pathROI.getShape();
			Shape shape = shapeProvider.getShape(pathROI, downsample);
//			Shape shape = PathROIToolsAwt.getShape(pathROI);
			// Only pass the colorFill if we have an area (i.e. not a line/polyline)
			if (pathROI.isArea())
				paintShape(shape, g, colorStroke, stroke, colorFill);
			else if (pathROI.isLine())
				paintShape(shape, g, colorStroke, stroke, null);
		} else if (pathROI.isPoint()) {
			paintPoints(pathROI, g2d, PathPrefs.pointRadiusProperty().get(), colorStroke, stroke, colorFill, downsample);
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
		
		private RectanglePool rectanglePool = new RectanglePool();
		private EllipsePool ellipsePool = new EllipsePool();
		private LinePool linePool = new LinePool();
		
		// TODO: Consider if it makes sense to map to PathHierarchyImageServer preferred downsamples
		// (Only if shape simplification is often used for detection objects)
		private Map<ROI, Shape> map50 = Collections.synchronizedMap(new WeakHashMap<>());
		private Map<ROI, Shape> map20 = Collections.synchronizedMap(new WeakHashMap<>());
		private Map<ROI, Shape> map10 = Collections.synchronizedMap(new WeakHashMap<>());
		private Map<ROI, Shape> map = Collections.synchronizedMap(new WeakHashMap<>());
		
		
		private Map<ROI, Shape> getMap(final ROI shape, final double downsample) {
			// If we don't have many vertices, just return the main map - no need to simplify
			int nVertices = shape.getNumPoints();
//			if (shape instanceof PolygonROI)
//				nVertices = ((PolygonROI)shape).nVertices();
//			else if (shape instanceof AreaROI)
//				nVertices = ((AreaROI)shape).nVertices();
			if (nVertices < MIN_SIMPLIFY_VERTICES || !shape.isArea())
				return map;
			
			if (downsample > 50)
				return map50;
			if (downsample > 20)
				return map20;
			if (downsample > 10)
				return map10;
			return map;
		}
		
		private static Shape simplifyByDownsample(final Shape shape, final double downsample) {
			try {
				if (downsample > 50)
					return ShapeSimplifier.simplifyPath(shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape), 50);
				if (downsample > 20)
					return ShapeSimplifier.simplifyPath(shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape), 20);
				if (downsample > 10)
					return ShapeSimplifier.simplifyPath(shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape), 10);
			} catch (Exception e) {
				logger.warn("Unable to simplify path: {}", e.getLocalizedMessage());
				logger.debug("", e);
			}
			return shape;
		}
		
		
		public Shape getShape(final ROI roi, final double downsample) {
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

			if (roi instanceof LineROI) {
				Line2D line = linePool.getShape();
				LineROI l = (LineROI)roi;
				line.setLine(l.getX1(), l.getY1(), l.getX2(), l.getY2());
				return line;
			}
			
			Map<ROI, Shape> map = getMap(roi, downsample);
//			map.clear();
			Shape shape = map.get(roi);
			if (shape == null) {
				shape = RoiTools.getShape(roi);
				// Downsample if we have to
				if (map != this.map) {
					// JTS methods are much slower
//					var simplifier = new DouglasPeuckerSimplifier(roi.getGeometry());
//					var simplifier = new VWSimplifier(roi.getGeometry());
//					simplifier.setDistanceTolerance(downsample);
//					simplifier.setEnsureValid(false);
//					shape = GeometryTools.geometryToShape(simplifier.getResultGeometry());
					shape = simplifyByDownsample(shape, downsample);
				}
				map.put(roi, shape);
			}
//			map.clear();
			return shape;
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
//					getConvexHull().draw(g, null, colorHull);
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
//			ellipse = new Ellipse2D.Double();
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
		 * Paint the handles onto a Graphics object.
		 * 
		 * @param g2d
		 * @param handleSize The width &amp; height of the shape used to draw the handles
		 * @param colorStroke
		 * @param colorFill
		 */
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
//			handleShape = new Ellipse2D.Double();
		
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
		 * Paint connections between objects (e.g. from Delaunay triangulation).
		 * 
		 * @param connections
		 * @param hierarchy
		 * @param g2d
		 * @param color
		 * @param downsampleFactor
		 * @param plane
		 */
		public static void paintConnections(final PathObjectConnections connections, final PathObjectHierarchy hierarchy, Graphics2D g2d, final Color color, final double downsampleFactor, final ImagePlane plane) {
			if (hierarchy == null || connections == null || connections.isEmpty())
				return;

			float alpha = (float)(1f - downsampleFactor / 5);
			alpha = Math.min(alpha, 0.4f);
			float thickness = PathPrefs.detectionStrokeThicknessProperty().get();
			if (alpha < .1f || thickness / downsampleFactor <= 0.25)
				return;

			g2d = (Graphics2D)g2d.create();

			//		Shape clipShape = g2d.getClip();
			g2d.setStroke(getCachedStroke(thickness));
//			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * .5f));
			//		g2d.setColor(ColorToolsAwt.getColorWithOpacity(getPreferredOverlayColor(), 1));

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

	
}
