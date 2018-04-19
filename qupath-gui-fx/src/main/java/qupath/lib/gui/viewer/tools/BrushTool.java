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

package qupath.lib.gui.viewer.tools;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.AWTAreaROI;
import qupath.lib.roi.AreaROI;
import qupath.lib.roi.ROIHelpers;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.ShapeSimplifierAwt;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.PathShape;

/**
 * Tool for drawing (and subtract from) freehand regions, optionally adapting brush size to magnification.
 * 
 * @author Pete Bankhead
 *
 */
public class BrushTool extends AbstractPathROITool {
	
	/**
	 * A collection of classes that should be ignored when
	 */
	static Set<PathClass> reservedPathClasses = Collections.singleton(PathClassFactory.getPathClass("Region"));
	
	double lastRequestedCursorDiameter = Double.NaN;
	Cursor requestedCursor;
	
//	/**
//	 * Cache the last 50 cursors we saw
//	 */
//	private static Map<String, Cursor> cursorCache = new LinkedHashMap<String, Cursor>() {
//		private static final long serialVersionUID = 1L;
//		@Override
//		protected boolean removeEldestEntry(Map.Entry<String, Cursor> eldest) {
//	        return size() > 50;
//	    }
//	};
	
	private SnapshotParameters snapshotParameters = new SnapshotParameters();
	
	public BrushTool(ModeWrapper modes) {
		super(modes);
		snapshotParameters.setFill(Color.TRANSPARENT);
	}
	
	
	protected Cursor getRequestedCursor() {
		// Display of image cursors seems buggy, at least on macOS?
		// TODO: Check if image cursors are buggy on all platforms or may be reinstated
		return Cursor.CROSSHAIR;
//		if (PathPrefs.getUseTileBrush())
//			return Cursor.CROSSHAIR;
//		
//		double res = 0.05;
//		
//		double diameter = getBrushDiameter() / viewer.getDownsampleFactor();
//		if (requestedCursor != null && Math.abs(diameter - lastRequestedCursorDiameter) < res)
//			return requestedCursor;
//		
//		Color color = viewer.getSuggestedOverlayColorFX();
//		String key = color.toString() + (int)Math.round(diameter * (1.0/res));
//		requestedCursor = cursorCache.get(key);
//		if (requestedCursor != null)
//			return requestedCursor;
//		
//		Ellipse e = new Ellipse(diameter/2, diameter/2);
//		e.setFill(null);
//		e.setStroke(color);
//		Image image = e.snapshot(snapshotParameters, null);
//		requestedCursor = new ImageCursor(image, diameter/2, diameter/2);
//		
//		this.registerTool(viewer);
//
//		cursorCache.put(key, requestedCursor);
//
//		return requestedCursor;
	}
	
	
	@Override
	public void mouseExited(MouseEvent e) {
//		ensureCursorType(Cursor.DEFAULT);
	}
	
	@Override
	public void mouseEntered(MouseEvent e) {
//		ensureCursorType(getRequestedCursor());
	}
	
	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
//		ensureCursorType(getRequestedCursor());
	}
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
//		ensureCursorType(getRequestedCursor());
	}
	
	
	@Override
	public void mousePressed(MouseEvent e) {
//		if (e.getClickCount() > 1)
//			super.mousePressed(e);
		
//		super.mousePressed(e);
		if (!e.isPrimaryButtonDown() || e.isConsumed()) {
            return;
        }
		
		ensureCursorType(getRequestedCursor());
		
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return;
		
		PathObject currentObject = viewer.getSelectedObject();
		
		// Determine if we are creating a new object
//		boolean createNew = currentObject == null || e.getClickCount() > 1;// || (!currentObject.getROI().contains(p.getX(), p.getY()) && !e.isAltDown());
		Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
//		boolean createNew = currentObject == null || !(currentObject instanceof PathAnnotationObject) || (currentObject.hasChildren()) || (PathPrefs.getBrushCreateNewObjects() && !ROIHelpers.areaContains(currentObject.getROI(), p.getX(), p.getY()) && !isSubtractMode(e));
		boolean createNew = currentObject == null || !(currentObject instanceof PathAnnotationObject) || (!currentObject.isEditable()) || currentObject.getROI().getZ() != viewer.getZPosition() || currentObject.getROI().getT() != viewer.getTPosition() || (!e.isShiftDown() && PathPrefs.getBrushCreateNewObjects() && !ROIHelpers.areaContains(currentObject.getROI(), p.getX(), p.getY()) && !isSubtractMode(e));
		
		// See if, rather than creating something, we can instead reactivate a current object
		boolean multipleClicks = e.getClickCount() > 1;
		if (multipleClicks || (createNew && !e.isShiftDown())) {
			// See if, rather than creating something, we can instead reactivate a current object
			if (multipleClicks) {
				PathObject objectSelectable = getSelectableObject(p.getX(), p.getY(), e.getClickCount() - 1);
				if (objectSelectable != null && objectSelectable.isEditable() && objectSelectable.getROI() instanceof PathArea) {
					createNew = false;
					viewer.setSelectedObject(objectSelectable);
					currentObject = objectSelectable;
				} else if (createNew) {
					viewer.setSelectedObject(null);
					currentObject = null;
				}
			} else {
					List<PathObject> listSelectable = getSelectableObjectList(p.getX(), p.getY());
					PathObject objectSelectable = null;
					for (int i = listSelectable.size()-1; i >= 0; i--) {
						PathObject temp = listSelectable.get(i);
						if (temp.isEditable() && temp instanceof PathAnnotationObject && temp.getROI() instanceof PathArea) { //temp.getROI() instanceof AreaROI) {
							objectSelectable = temp;
							break;
						}
					}
					if (objectSelectable != null) {
						createNew = false;
						viewer.setSelectedObject(objectSelectable);
						currentObject = objectSelectable;
					} else if (createNew) {
						viewer.setSelectedObject(null);
						currentObject = null;
					}
			}
		}
		
		// Can only modify annotations
		// TODO: Check for object being locked!
		if (!createNew && !(currentObject instanceof PathAnnotationObject && currentObject.getROI() instanceof PathShape))
			return;
				
		// Need to remove the object from the hierarchy while editing it
		if (!createNew && currentObject != null) {
			hierarchy.removeObject(currentObject, true);
		}
		
		
		PathShape shapeROI = createNew ? null : (PathShape)currentObject.getROI();
		if (createNew) {
			creatingTiledROI = false; // Reset this
			viewer.setSelectedObject(new PathAnnotationObject(new AWTAreaROI(new Rectangle2D.Double(p.getX(), p.getY(), 0, 0), -1, viewer.getZPosition(), viewer.getTPosition())));
		} else
			viewer.setSelectedObject(getUpdatedObject(e, shapeROI, currentObject, -1));
	}
	
	
	
	
	@Override
	public void mouseDragged(MouseEvent e) {
		// Note: if the 'freehand' part of the polygon creation isn't desired, just comment out this whole method
		super.mouseDragged(e);
		
		ensureCursorType(getRequestedCursor());
		if (!e.isPrimaryButtonDown()) {
            return;
        }
		
		// Can only modify annotations
		// TODO: Check for object being locked!
		PathObject pathObject = viewer.getSelectedObject();
		if (pathObject == null || !pathObject.isAnnotation())
			return;

		ROI currentROI = pathObject.getROI();
		if (!(currentROI instanceof PathShape))
			return;
		
		PathShape shapeROI = (PathShape)currentROI;
		
		PathObject pathObjectUpdated = getUpdatedObject(e, shapeROI, pathObject, -1);
		viewer.setSelectedObject(pathObjectUpdated);
	}
	
	
	
	protected boolean isSubtractMode(MouseEvent e) {
		return e == null ? false : e.isAltDown();
	}
	
	
	private PathObject getUpdatedObject(MouseEvent e, PathShape shapeROI, PathObject currentObject, double flatness) {
		Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
		PathShape shapeNew;
		boolean subtractMode = isSubtractMode(e);
		Shape shapeCurrent = shapeROI == null ? null : PathROIToolsAwt.getShape(shapeROI);
		Shape shapeDrawn = createShape(p.getX(), p.getY(),
				PathPrefs.getUseTileBrush() && !e.isShiftDown(),
				subtractMode ? null : shapeCurrent);
		if (shapeROI != null) {
			// Check to see if any changes are required at all
			if (shapeDrawn == null || (subtractMode && !shapeCurrent.intersects(shapeDrawn.getBounds2D())) || 
					(!subtractMode && shapeCurrent.contains(shapeDrawn.getBounds2D())))
				return currentObject;
			
			// TODO: Consider whether a preference should be used rather than the shift key?
			// Anyhow, this will switch to 'dodge' mode, and avoid overlapping existing annotations
			boolean avoidOtherAnnotations = e.isShiftDown();
			if (subtractMode) {
				// If subtracting... then just subtract
				shapeNew = PathROIToolsAwt.combineROIs(shapeROI,
						new AWTAreaROI(shapeDrawn, shapeROI.getC(), shapeROI.getZ(), shapeROI.getT()), PathROIToolsAwt.CombineOp.SUBTRACT, flatness);
			} else if (avoidOtherAnnotations) {
				Rectangle bounds2 = shapeDrawn.getBounds();
				Collection<PathObject> annotations = viewer.getHierarchy().getObjectsForRegion(PathAnnotationObject.class, ImageRegion.createInstance(
						bounds2.x, bounds2.y, bounds2.width, bounds2.height, viewer.getZPosition(), viewer.getTPosition()), null);
				Area area = new Area(shapeCurrent);
				area.add(new Area(shapeDrawn));
				if (!annotations.isEmpty()) {
					for (PathObject pathObject : annotations) {
						if (reservedPathClasses.contains(pathObject.getPathClass()))
							continue;
						if (pathObject.getROI() instanceof PathArea) {
							area.subtract(PathROIToolsAwt.getArea(pathObject.getROI()));
						}
					}
				}
				shapeNew = PathROIToolsAwt.getShapeROI(area, shapeROI.getC(), shapeROI.getZ(), shapeROI.getT());
			} else {
				// Just add, regardless of whether there are other annotations below or not
				shapeNew = PathROIToolsAwt.combineROIs(shapeROI,
						new AWTAreaROI(shapeDrawn, shapeROI.getC(), shapeROI.getZ(), shapeROI.getT()), PathROIToolsAwt.CombineOp.ADD, flatness);
			}
			
			// Convert complete polygons to areas
			if (shapeNew instanceof PolygonROI && ((PolygonROI)shapeNew).nVertices() > 50) {
				shapeNew = new AWTAreaROI(PathROIToolsAwt.getShape(shapeNew), shapeNew.getC(), shapeNew.getZ(), shapeNew.getT());
			}
		} else {
			shapeNew = new AWTAreaROI(shapeDrawn, -1, viewer.getZPosition(), viewer.getTPosition());
		}
		
		if (currentObject instanceof PathAnnotationObject) {
			((PathAnnotationObject)currentObject).setROI(shapeNew);
			return currentObject;
		}
		
//		shapeNew = new PathAreaROI(new Area(shapeNew.getShape()));
		PathAnnotationObject pathObjectNew = new PathAnnotationObject(shapeNew);
		if (currentObject != null) {
			pathObjectNew.setName(currentObject.getName());
			pathObjectNew.setColorRGB(currentObject.getColorRGB());
			pathObjectNew.setPathClass(currentObject.getPathClass());
		}
		return pathObjectNew;
	}
	
	
	@Override
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
		
		ensureCursorType(Cursor.DEFAULT);
		
		if (e.isConsumed())
			return;

		PathObject currentObject = viewer.getSelectedObject();
		if (currentObject == null)
			return;
		
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		// Ensure the object is in the hierarchy, if it is non-empty
		if (currentObject.getParent() == null) {
			if (currentObject.getROI() == null || currentObject.getROI().isEmpty())
				viewer.setSelectedObject(null);
			else {
				// Create a polygon ROI if possible
				ROI pathROI = viewer.getCurrentROI();
				if (pathROI instanceof AreaROI) {
					// Simplify the shape as we go to discard unnecessary vertices
//					int nVertices = 
//							(pathROI instanceof PolygonROI) ? ((PolygonROI)pathROI).nVertices() :
//							((pathROI instanceof AreaROI) ? ((AreaROI)pathROI).nVertices() : 1);
							
					pathROI = ShapeSimplifierAwt.simplifyShape((PathShape)pathROI, 0.5);
//							pathROI = ShapeSimplifierAwt.simplifyShape((PathShape)pathROI, viewer.getDownsampleFactor());
					
//					if (nVertices > 500)
//						pathROI = ShapeSimplifierAwt.simplifyShape((PathShape)pathROI, 2);
//					else if (nVertices > 200)
//						pathROI = ShapeSimplifierAwt.simplifyShape((PathShape)pathROI, 1);						
//					else if (nVertices > 20)
//						pathROI = ShapeSimplifierAwt.simplifyShape((PathShape)pathROI, .5);						
//					else
//						pathROI = ShapeSimplifierAwt.simplifyShape((PathShape)pathROI, .25);						
					currentObject = getUpdatedObject(e, (PathShape)pathROI, currentObject, -1);
//					currentObject = getUpdatedObject(e, (PathShape)pathROI, currentObject, 2);
					viewer.setSelectedObject(currentObject);
				}
				hierarchy.addPathObject(currentObject, true);
			}
		}
		else if (currentObject.hasROI()) {
			if (currentObject.getROI().isEmpty()) {
				hierarchy.removeObject(currentObject, true);			
				viewer.setSelectedObject(null);
			}
		}
		
//		if (currentObject.getParent() != null)
//			hierarchy.removeObject(currentObject, true);
//		else {
//			hierarchy.addPathObject(currentObject, true);
//			//		viewer.createAnnotationObject(currentROI);
//		}
//		if (PathPrefs.getReturnToPan())
//			modes.setMode(Modes.MOVE);

	}
	
	
	protected double getBrushDiameter() {
		if (PathPrefs.getBrushScaleByMag())
			return PathPrefs.getBrushDiameter() * viewer.getDownsampleFactor();
		else
			return PathPrefs.getBrushDiameter();
	}
	
	
	/**
	 * Create a new Shape using the specified tool, assuming a user click/drag at the provided x &amp; y coordinates.
	 * 
	 * @param x
	 * @param y
	 * @param useTiles If true, request generating a shape from existing tile objects.
	 * @param addToShape If provided, it can be assumed that any new shape ought to be added to this one.
	 *                   The purpose is that this method may (optionally) use the shape to refine the one it will generate, 
	 *                   e.g. to avoid having isolated or jagged boundaries.
	 * @return
	 */
	protected Shape createShape(double x, double y, boolean useTiles, Shape addToShape) {
		
		// See if we're on top of a tile
		// TODO: Add preference to turn on/off tile brush
		if (useTiles) {
			List<PathObject> listSelectable = getSelectableObjectList(x, y);
			for (PathObject temp : listSelectable) {
//				if ((temp instanceof PathDetectionObject) && temp.getROI() instanceof PathArea)
				if (temp instanceof PathTileObject && temp.getROI() instanceof PathArea && !(temp.getROI() instanceof RectangleROI)) {
					creatingTiledROI = true;
					return PathROIToolsAwt.getShape(temp.getROI());
				}
			}
			// If we're currently creating a tiled, ROI, but now not clicked on a tile, just return
			if (creatingTiledROI)
				return null;
		}

		// Compute a diameter scaled according to the pressure being applied
		double diameter = getBrushDiameter();
		Shape shape = new Ellipse2D.Double(x-diameter/2, y-diameter/2, diameter, diameter);
		// Clip over the image boundary
		Rectangle2D shapeBounds = shape.getBounds2D();
		viewer.getServerBounds();
		Rectangle2D bounds = AwtTools.getBounds(viewer.getServerBounds());
		if (!bounds.contains(shapeBounds)) {
			Area area = new Area(shape);
			area.intersect(new Area(bounds));
			return area;
		}
		
		return shape;
	}
	
	private boolean creatingTiledROI = false;

	@Override
	protected ROI createNewROI(double x, double y, int z, int t) {
		creatingTiledROI = false;
		Shape shape = createShape(x, y, PathPrefs.getUseTileBrush(), null);
		return new AWTAreaROI(shape, -1, z, t);
//		return new PathPolygonROI(x, y, -1, z, t);
	}
	
}
