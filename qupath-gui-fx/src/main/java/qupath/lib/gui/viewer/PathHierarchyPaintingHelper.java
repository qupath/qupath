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

package qupath.lib.gui.viewer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.color.ColorToolsAwt;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.helpers.MeasurementMapper;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnectionGroup;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectColorToolsAwt;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.plugins.ParallelTileObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.AreaROI;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.ShapeSimplifierAwt;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathPoints;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;


/**
 * Static methods to assist with painting PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
public class PathHierarchyPaintingHelper {
	
	final private static Logger logger = LoggerFactory.getLogger(PathHierarchyPaintingHelper.class);

	public static int maxThumbnailWidth = 4000;
	
	private static ShapeProvider shapeProvider = new ShapeProvider();
	
	private static Map<Number, Stroke> strokeMap = new HashMap<>();
	
	private PathHierarchyPaintingHelper() {}

	/**
	 * Create a thumbnail image, with the overlay painted.
	 * If a BufferedImage is supplied, it will be used if it has the required width &amp; height - otherwise a new one will be generated.
	 * 
	 * @param overlayOptions
	 * @param serverWidth
	 * @param serverHeight
	 * @param imgThumbnail
	 * @return
	 */
	public static BufferedImage createThumbnail(PathObjectHierarchy hierarchy, OverlayOptions overlayOptions, int serverWidth, int serverHeight, BufferedImage imgThumbnail, ImageRegion region) {
		double downsample = 1;
		if (serverWidth > maxThumbnailWidth)
			downsample = (double)serverWidth / maxThumbnailWidth;
		
		// Create a suitably-large image for all the tiles, extract the graphics & transform
		int w = (int)(serverWidth / downsample + .5);
		int h = (int)(serverHeight / downsample + .5);
		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
		if (imgThumbnail == null || imgThumbnail.getWidth() != w || imgThumbnail.getHeight() != h) {
			imgThumbnail = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
		} else {
			// Clear the existing image
			Graphics2D gTemp = imgThumbnail.createGraphics();
			gTemp.scale(1.0/downsample, 1.0/downsample);
			gTemp.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f));
			if (region == null)
				gTemp.fillRect(0, 0, w, h);
			else
				gTemp.fillRect(region.getX(), region.getY(), region.getWidth(), region.getHeight());
			gTemp.dispose();
		}
		
		Graphics2D g2d = imgThumbnail.createGraphics();
		g2d.scale(1.0/downsample, 1.0/downsample);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		logger.trace("Creating thumbnail for " + region);
		paintSpecifiedObjects(g2d, null, hierarchy.getObjectsForRegion(PathDetectionObject.class, region, null), overlayOptions, hierarchy.getSelectionModel(), downsample);

		g2d.dispose();
		return imgThumbnail;
	}
	
	
	public static void paintSpecifiedObjects(Graphics2D g2d, Rectangle boundsDisplayed, Collection<PathObject> pathObjects, OverlayOptions overlayOptions, PathObjectSelectionModel selectionModel, double downsample) {
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
	
	
	
	public static void paintTMAGrid(Graphics2D g2d, TMAGrid tmaGrid, OverlayOptions overlayOptions, PathObjectSelectionModel selectionModel, double downsampleFactor) {
		if (tmaGrid == null)
			return;
		Rectangle boundsDisplayed = g2d.getClipBounds();
		// Paint the TMA grid, if required
		if (!overlayOptions.getShowTMAGrid())
			return;
		Stroke strokeThick = getCachedStroke(PathPrefs.getThickStrokeThickness() * downsampleFactor);
		g2d.setStroke(strokeThick);
		// Paint the TMA grid
		Color colorGrid = ColorToolsAwt.getCachedColor(PathPrefs.getTMAGridColor());
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
				PathHierarchyPaintingHelper.paintObject(core, false, g2d, boundsDisplayed, overlayOptions, selectionModel, downsampleFactor);
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
				if (width < core.getROI().getBoundsWidth() / downsampleFactor)
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
				
				Color colorPainted = PathObjectColorToolsAwt.getDisplayedColorAWT(core);
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
		boolean isSelected = (selectionModel != null && selectionModel.isSelected(pathObject)) && (PathPrefs.getUseSelectedColor() || !pathObject.isPoint());
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
			if (pathObject.isPoint() || boundsDisplayed == null || 
					pathROI instanceof LineROI || 
					boundsDisplayed.intersects(roiBoundsX, roiBoundsY, Math.max(roiBoundsWidth, 1), Math.max(roiBoundsHeight, 1))) {
			
				// Paint the ROI, if necessary
				if (isSelected || (overlayOptions.getShowObjects() && isDetectedObject) || (overlayOptions.getShowAnnotations() && pathObject.isAnnotation()) || (overlayOptions.getShowTMAGrid() && pathObject.isTMACore())) {
		
					boolean doFill = overlayOptions.getFillObjects() || pathObject instanceof ParallelTileObject;
					boolean doOutline = true;
					
					Color color = null;
					boolean useMapper = false;
					double fillOpacity = .75;
					if (isSelected && PathPrefs.getUseSelectedColor() && PathPrefs.getSelectedObjectColor() != null)
						color = ColorToolsAwt.getCachedColor(PathPrefs.getSelectedObjectColor());
					else {
						MeasurementMapper mapper = overlayOptions.getMeasurementMapper();
						useMapper = mapper != null && mapper.isValid() && pathObject.isDetection();
						if (useMapper) {
							if (pathObject.hasMeasurements()) {
								Integer rgb = mapper.getColorForObject(pathObject);
								// If the mapper returns null, the object shouldn't be painted
								if (rgb == null)
									return false;
								color = ColorToolsAwt.getCachedColor(rgb, mapper.getColorMapper().hasAlpha());
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
							Integer rgb = PathObjectColorToolsAwt.getDisplayedColor(pathObject);
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
//							g.setStroke(getCachedStroke(overlayOptions.getThinStrokeThickness()));
							g.fillRect(x, y, w, h);
						}
						painted = true;
					} else {
						Stroke stroke = null;
						// Decide whether to fill or not
						Color colorFill = doFill && (isDetectedObject || pathObject.isPoint()) ? color : null;
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
							} else if (pathObject instanceof PathTileObject && pathClass == null && color !=null && color.getRGB() == PathPrefs.getTileColor()) {
								// Don't fill in empty, unclassified tiles
								colorFill = null; //DisplayHelpers.getMoreTranslucentColor(colorFill);
							}
						}
//						Color colorStroke = doOutline ? (colorFill == null ? color : (downsample > overlayOptions.getThinStrokeThickness() ? null : DisplayHelpers.darkenColor(color))) : null;
						Color colorStroke = doOutline ? (colorFill == null ? color : ColorToolsAwt.darkenColor(color)) : null;
						
						// For thick lines, antialiasing is very noticeable... less so for thin lines (of which there may be a huge number)
						if (isDetectedObject) {
							// Detections inside detections get half the line width
							if (pathObject.getParent() instanceof PathDetectionObject)
								stroke = getCachedStroke(PathPrefs.getThinStrokeThickness() / 2.0);
							else
								stroke = getCachedStroke(PathPrefs.getThinStrokeThickness());
						}
						else {
							double thicknessScale = downsample * (isSelected && !PathPrefs.getUseSelectedColor() ? 1.6 : 1);
							stroke = getCachedStroke(PathPrefs.getThickStrokeThickness() * thicknessScale);
						}
						
						g.setStroke(stroke);
						if (pathObject instanceof PathCellObject) {
							PathCellObject cell = (PathCellObject)pathObject;
							if (overlayOptions.getShowCellBoundaries())
								paintROI(pathROI, g, colorStroke, stroke, colorFill, downsample);
							if (overlayOptions.getShowCellNuclei())
								paintROI(cell.getNucleusROI(), g, colorStroke, stroke, colorFill, downsample);
							painted = true;
						} else {
							if ((overlayOptions.getFillAnnotations() && pathObject.isAnnotation() && pathObject.getPathClass() != PathClassFactory.getRegionClass()) || (pathObject.isTMACore() && overlayOptions.getShowTMACoreLabels()))
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
			for (PathObject childObject : pathObject.getChildObjects()) {
				// Only call the painting method if required
				ROI childROI = childObject.getROI();
				if ((childROI != null && boundsDisplayed.intersects(childROI.getBoundsX(), childROI.getBoundsY(), childROI.getBoundsWidth(), childROI.getBoundsHeight())) || childObject.hasChildren())
					painted = paintObject(childObject, paintChildren, g, boundsDisplayed, overlayOptions, selectionModel, downsample) | painted;
			}
		}
		return painted;
	}
	
	
	static Rectangle2D boundsTemp = new Rectangle2D.Double();
	
	
	public static void paintROI(ROI pathROI, Graphics2D g, Color colorStroke, Stroke stroke, Color colorFill, double downsample) {
		if (pathROI == null)
			return;
//		pathROI.draw(g, colorStroke, colorFill);
		
		if (colorStroke == null && colorFill == null)
			return;
		
		Graphics2D g2d = (Graphics2D)g.create();
		if (pathROI instanceof PathShape) {
			Shape shape = shapeProvider.getShape((PathShape)pathROI, downsample);
//			Shape shape = PathROIToolsAwt.getShape(pathROI);
			paintShape(shape, g, colorStroke, stroke, colorFill, downsample);
		} else if (pathROI instanceof PathPoints) {
			paintPoints((PathPoints)pathROI, g2d, PathPrefs.getDefaultPointRadius(), colorStroke, stroke, colorFill, downsample);
		}
		g2d.dispose();
	}
	

	static abstract class ShapePool<T extends Shape> {
		
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
	 * 
	 * The purpose is to facilitate painting, i.e. shapes are provided for use before being discarded.
	 * 
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
		private Map<PathShape, Shape> map50 = Collections.synchronizedMap(new WeakHashMap<>());
		private Map<PathShape, Shape> map20 = Collections.synchronizedMap(new WeakHashMap<>());
		private Map<PathShape, Shape> map10 = Collections.synchronizedMap(new WeakHashMap<>());
		private Map<PathShape, Shape> map = Collections.synchronizedMap(new WeakHashMap<>());
		
		
		private Map<PathShape, Shape> getMap(final PathShape shape, final double downsample) {
			// If we don't have many vertices, just return the main map - no need to simplify
			int nVertices = 0;
			if (shape instanceof PolygonROI)
				nVertices = ((PolygonROI)shape).nVertices();
			else if (shape instanceof AreaROI)
				nVertices = ((AreaROI)shape).nVertices();
			if (nVertices < MIN_SIMPLIFY_VERTICES)
				return map;
			
			if (downsample > 50)
				return map50;
			if (downsample > 20)
				return map20;
			if (downsample > 10)
				return map10;
			return map;
		}
		
		private Shape simplifyByDownsample(final Shape shape, final double downsample) {
			if (downsample > 50)
				return ShapeSimplifierAwt.simplifyPath(shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape), 100);
			if (downsample > 20)
				return ShapeSimplifierAwt.simplifyPath(shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape), 40);
			if (downsample > 10)
				return ShapeSimplifierAwt.simplifyPath(shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape), 20);
			return shape;
		}
		
		
		public Shape getShape(final PathShape roi, final double downsample) {
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

			Map<PathShape, Shape> map = getMap(roi, downsample);
//			map.clear();
			Shape shape = map.get(roi);
			if (shape == null) {
				shape = PathROIToolsAwt.getShape(roi);
				// Downsample if we have to
				if (map != this.map)
					shape = simplifyByDownsample(shape, downsample);
				map.put(roi, shape);
			}
//			map.clear();
			return shape;
		}
		
	}
	
	
	public static void paintShape(Shape shape, Graphics2D g2d, Color colorStroke, Stroke stroke, Color colorFill, double downsample) {
		
//		if (shape instanceof Path2D && downsample > 10) {
//			Shape shape2 = downsampledShapes.get(shape);
//			if (shape2 == null) {
//				shape2 = ShapeSimplifierAwt.simplifyPath((Path2D)shape, 10);
//				if (shape2 != null) {
//					downsampledShapes.put(shape, shape2);
//					shape = shape2;
//				}
//			}
//		}
		
		
//		if (stroke != null)
//			g2d.setStroke(stroke);
//		g2d.setColor(colorStroke);
//		System.out.println("Drawing: " + AWTAreaROI.getVertices(shape).size());
//		for (Vertices v : AWTAreaROI.getVertices(shape)) {
//			g2d.draw(PathROIToolsAwt.getShape(new PolygonROI(v.getPoints())));
//			//				g2d.fill(PathROIToolsAwt.getShape(new PolygonROI(v.getPoints())));
//		}
		
		
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
	
	
	public static void paintPoints(PathPoints pathPoints, Graphics2D g2d, double radius, Color colorStroke, Stroke stroke, Color colorFill, double downsample) {
		PointsROI pathPointsROI = pathPoints instanceof PointsROI ? (PointsROI)pathPoints : null;
		if (pathPointsROI != null && PathPrefs.getShowPointHulls()) {
			PathArea convexHull = pathPointsROI.getConvexHull();
			if (convexHull != null) {
				Color colorHull = colorFill != null ? colorFill : colorStroke;
				colorHull = ColorToolsAwt.getColorWithOpacity(colorHull, 0.1);
				if (colorHull != null)
					paintShape(PathROIToolsAwt.getShape(convexHull), g2d, null, null, colorHull, downsample);
//					getConvexHull().draw(g, null, colorHull);
			}
		}
		
		Ellipse2D ellipse = new Ellipse2D.Double();
		
//		double radius = pathPointsROI == null ? PointsROI.getDefaultPointRadius() : pathPointsROI.getPointRadius();
		// Ensure that points are drawn with at least a radius of one, after any transforms have been applied
		double scale = g2d.getTransform().getScaleX();
		radius = (Math.max(1 / scale, radius));
		
		g2d.setStroke(stroke);
		for (Point2 p : pathPoints.getPointList()) {
			ellipse.setFrame(p.getX()-radius, p.getY()-radius, radius*2, radius*2);
			if (colorFill != null) {
				g2d.setColor(colorFill);
				g2d.fill(ellipse);
			}
			if (colorStroke != null) {
				g2d.setColor(colorStroke);
				g2d.draw(ellipse);
			}
		}
	}
	
	
	
	static Stroke getCachedStroke(final Number thickness) {
		Stroke stroke = strokeMap.get(thickness);
		if (stroke == null) {
			stroke = new BasicStroke(thickness.floatValue());
			strokeMap.put(thickness, stroke);
		}
		return stroke;
	}
	
	public static Stroke getCachedStroke(final int thickness) {
		return getCachedStroke(Integer.valueOf(thickness));
	}
	
	public static Stroke getCachedStroke(final double thickness) {
		if (thickness == Math.rint(thickness))
			return getCachedStroke((int)thickness);
		return getCachedStroke(Float.valueOf((float)thickness));
	}
	
	
	/**
	 * Paint the handles onto a Graphics object, if we have a suitable (non-point) ROI.
	 * 
	 * @param g2d
	 * @param handleSize The width &amp; height of the shape used to draw the handles
	 * @param colorStroke
	 * @param colorFill
	 */
	public static void paintHandles(final RoiEditor roiEditor, final Graphics2D g2d, final double handleSize, final Color colorStroke, final Color colorFill) {		
		if (!(roiEditor.getROI() instanceof PointsROI))
			paintHandles(roiEditor.getHandles(), g2d, handleSize, colorStroke, colorFill);
	}

		/**
		 * Paint the handles onto a Graphics object.
		 * 
		 * @param g2d
		 * @param handleSize The width &amp; height of the shape used to draw the handles
		 * @param colorStroke
		 * @param colorFill
		 */
		public static void paintHandles(final List<Point2> handles, final Graphics2D g2d, final double handleSize, final Color colorStroke, final Color colorFill) {		
			RectangularShape handleShape = new Rectangle2D.Double();
//			handleShape = new Ellipse2D.Double();
		
		for (Point2 p : handles) {
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
	}

		/**
		 * Paint connections between objects (e.g. from Delaunay triangulation).
		 * 
		 * @param connections
		 * @param hierarchy
		 * @param g2d
		 * @param color
		 * @param downsampleFactor
		 */
		public static void paintConnections(final PathObjectConnections connections, final PathObjectHierarchy hierarchy, Graphics2D g2d, final Color color, final double downsampleFactor) {
			if (hierarchy == null || connections == null || connections.isEmpty())
				return;

			float alpha = (float)(1f - downsampleFactor / 5);
			alpha = Math.min(alpha, 0.25f);
			float thickness = PathPrefs.getThinStrokeThickness();
			if (alpha < .1f || thickness / downsampleFactor <= 0.5)
				return;

			g2d = (Graphics2D)g2d.create();

			//		Shape clipShape = g2d.getClip();
			g2d.setStroke(getCachedStroke(thickness));
//			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * .5f));
			//		g2d.setColor(ColorToolsAwt.getColorWithOpacity(getPreferredOverlayColor(), 1));

			g2d.setColor(ColorToolsAwt.getColorWithOpacity(color.getRGB(), alpha));
//			g2d.setColor(Color.BLACK);
			Line2D line = new Line2D.Double();
			
			// We can have trouble whenever two objects are outside the clip, but their connections would be inside it
			// Here, we just enlarge the region (by quite a lot)
			// It's not guaranteed to work, but it usually does... and avoids much expensive computations
			Rectangle bounds = g2d.getClipBounds();
			int factor = 1;
			Rectangle bounds2 = factor > 0 ? new Rectangle(bounds.x-bounds.width*factor, bounds.y-bounds.height*factor, bounds.width*(factor*2+1), bounds.height*(factor*2+1)) : bounds;
			ImageRegion imageRegion = AwtTools.getImageRegion(bounds2, 0, 0);
//			ImageRegion imageRegion = AwtTools.getImageRegion(bounds, 0, 0);

			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

//			g2d.draw(g2d.getClipBounds());
			
			Collection<PathObject> pathObjects = hierarchy.getObjectsForRegion(PathDetectionObject.class, imageRegion, null);
//			Collection<PathObject> pathObjects = hierarchy.getObjects(null, PathDetectionObject.class);
			//		double threshold = downsampleFactor*downsampleFactor*4;
				for (PathObject pathObject : pathObjects) {
					ROI roi = PathObjectTools.getROI(pathObject, true);
					double x1 = roi.getCentroidX();
					double y1 = roi.getCentroidY();
					for (PathObjectConnectionGroup dt : connections.getConnectionGroups()) {
					for (PathObject siblingObject : dt.getConnectedObjects(pathObject)) {
						ROI roi2 = PathObjectTools.getROI(siblingObject, true);
						double x2 = roi2.getCentroidX();
						double y2 = roi2.getCentroidY();
						if (bounds.intersectsLine(x1, y1, x2, y2)) {
							line.setLine(x1, y1, x2, y2);
							g2d.draw(line);
						}
					}
				}
			}

			g2d.dispose();
		}

	
//	@Override
//	public void draw(Graphics g, Color colorStroke, Color colorFill) {
//		if (colorStroke == null && colorFill == null)
//			return;
//		
//		// Complex shapes can be very slow to draw - if we require a highly downsampled version,
//		// then first cache a simplified version and draw that instead
//		Graphics2D g2d = (Graphics2D)g.create();
//		// Determine the scale (squared - no need to compute square root)
//		AffineTransform transform = g2d.getTransform();
//		double scaleSquared = transform.getScaleX()*transform.getScaleX() + transform.getShearX()*transform.getShearX();
//		if (scaleSquared > 0.1*0.1) {
//			super.draw(g, colorStroke, colorFill);
//			return;
//		}
//		
////		shapeSimplified = null;
//		if (shapeSimplified == null) {
//			shapeSimplified = ShapeSimplifier.simplifyPath(shape, 25);
//		}
//		if (colorFill != null) {
//			g2d.setColor(colorFill);
//			g2d.fill(shapeSimplified); 
//		}
//		if (colorStroke != null) {
//			g2d.setColor(colorStroke);
//			g2d.draw(shapeSimplified);
//		}
//		g2d.dispose();
//	}
	
	
//	public void draw(Graphics g, Color colorStroke, Color colorFill);
	
		
	
}
