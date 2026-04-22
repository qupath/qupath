/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer.tools.handlers;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import javafx.scene.Cursor;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.robot.Robot;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.simplify.VWSimplifier;
import org.locationtech.jts.util.GeometricShapeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.tools.QuPathPenManager;
import qupath.lib.gui.viewer.tools.QuPathPenManager.PenInputManager;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.List;

/**
 * Tool for drawing (and subtract from) freehand regions, optionally adapting brush size to magnification.
 * 
 * @author Pete Bankhead
 *
 */
public class BrushToolEventHandler extends AbstractPathROIToolEventHandler<InputEvent> implements NotifiableEventHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(BrushToolEventHandler.class);

	/**
	 * Robot, used to get mouse coordinates whenever a mouse event is not available
	 */
	private static final Robot robot = new Robot();

	/**
	 * The object currently being edited by the Brush.
	 * This is set when the mouse is pressed, to avoid relying on QuPathViewer.getSelectedObject() 
	 * (in case something has sneakily changed this).
	 */
	private PathObject currentObject;

	private Point2D lastPoint;

	private final BrushLimits brushLimits = new BrushLimits();

	private final ViewerListener listener = new ViewerListener();

	/**
	 * Returns false.
	 */
	@Override
	protected boolean preferReturnToMove() {
		return false;
	}

	protected Cursor getRequestedCursor() {
		return Cursor.CROSSHAIR;
	}
	
	
	@Override
	public void mouseExited(MouseEvent e) {
		brushLimits.setVisible(false);
	}
	
	@Override
	public void mouseEntered(MouseEvent e) {
		updateLimitsAndCursor(e);
	}
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		updateLimitsAndCursor(e);
	}
	
	
	@Override
	public void mousePressed(MouseEvent e) {
		if (!e.isPrimaryButtonDown() || e.isConsumed()) {
            return;
        }

		updateLimitsAndCursor(e);

		var viewer = getViewer();
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return;
		
		PathObject currentObject = viewer.getSelectedObject();
		
		// Ignore the current object if it belongs to a different image plane
		if (currentObject == null ||
				PathPrefs.selectionModeStatus().get() ||
				!(currentObject instanceof PathAnnotationObject) || 
				(!currentObject.isEditable()) || 
				currentObject.getROI().getZ() != viewer.getZPosition() || 
				currentObject.getROI().getT() != viewer.getTPosition()) {
			currentObject = null;
		}
		
		// Determine if we are creating a new object
//		boolean createNew = currentObject == null || e.getClickCount() > 1;// || (!currentObject.getROI().contains(p.getX(), p.getY()) && !e.isAltDown());
		Point2D p = mouseLocationToImage(e, false, requestPixelSnapping());
		double xx = p.getX();
		double yy = p.getY();
		if (xx < 0 || yy < 0 || xx >= viewer.getServerWidth() || yy >= viewer.getServerHeight()) {
			// Even if clicking outside the image, we want the brush to function when dragged inside the image
			if (currentObject != null) {
				this.currentObject = currentObject;
				lastPoint = p;
				// Ensure handles go away if we have an object
				viewer.getROIEditor().setROI(null);
			} else {
				this.currentObject = null;
			}
			return;
		}
		
//		boolean createNew = currentObject == null || !(currentObject instanceof PathAnnotationObject) || (currentObject.hasChildren()) || (PathPrefs.getBrushCreateNewObjects() && !ROIHelpers.areaContains(currentObject.getROI(), p.getX(), p.getY()) && !isSubtractMode(e));
		boolean createNew = currentObject == null || 
				(!e.isShiftDown() && PathPrefs.brushCreateNewObjectsProperty().get() && !RoiTools.areaContains(currentObject.getROI(), p.getX(), p.getY()) && !isSubtractMode(e));
		if (isSubtractMode(e))
			createNew = false;
		
		// See if, rather than creating something, we can instead reactivate a current object
		boolean multipleClicks = e.getClickCount() > 1;
		if (!PathPrefs.selectionModeStatus().get() && (multipleClicks || (createNew && !e.isShiftDown()))) {
			// See if, rather than creating something, we can instead reactivate a current object
			if (multipleClicks) {
				PathObject objectSelectable = ToolUtils.getSelectableObject(viewer, p.getX(), p.getY(), e.getClickCount() - 1);
				if (objectSelectable != null && objectSelectable.isEditable() && objectSelectable.hasROI() && objectSelectable.getROI().isArea()) {
					createNew = false;
					viewer.setSelectedObject(objectSelectable);
					currentObject = objectSelectable;
				} else if (createNew) {
					viewer.setSelectedObject(null);
					currentObject = null;
				}
			} else if (!PathPrefs.selectionModeStatus().get()) {
					List<PathObject> listSelectable = ToolUtils.getSelectableObjectList(viewer, p.getX(), p.getY());
					PathObject objectSelectable = null;
					for (PathObject temp : listSelectable) {
						if (temp.isEditable() && temp instanceof PathAnnotationObject && temp.hasROI() && temp.getROI().isArea()) { //temp.getROI() instanceof AreaROI) {
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
		if (!createNew && !(currentObject != null && currentObject.isAnnotation() && currentObject.isEditable() && RoiTools.isShapeROI(currentObject.getROI())))
			return;

		updatingConstrainingObjects(viewer, xx, yy, Collections.singleton(currentObject));

		// Need to remove the object from the hierarchy while editing it
		if (!createNew && currentObject != null) {
			hierarchy.removeObjectWithoutUpdate(currentObject, true);
		}

		ROI shapeROI = createNew ? null : currentObject.getROI();
		if (createNew) {
			creatingTiledROI = false; // Reset this
			this.currentObject = createNewAnnotation(e, p.getX(), p.getY());
			viewer.getROIEditor().setROI(null);
		} else {
			this.currentObject = getUpdatedObject(e, shapeROI, currentObject);
			viewer.setSelectedObject(this.currentObject);
			viewer.getROIEditor().setROI(null); // Avoids handles appearing?
		}		
		lastPoint = p;
	}

	private void updateLimitsAndCursor() {
		ensureCursorType(Cursor.CROSSHAIR);
		var viewer = getViewer();
		if (viewer != null) {
			double screenX = robot.getMouseX();
			double screenY = robot.getMouseY();
			var p = viewer.getView().screenToLocal(screenX, screenY);
			updateLimits(p.getX(), p.getY());
		}
	}

	private void updateLimitsAndCursor(MouseEvent e) {
		// Workaround for bug (at least on macOS) where cursor doesn't update properly when
		// mouse enters from outside the app
		if (e.getEventType() == MouseEvent.MOUSE_ENTERED) {
			ensureCursorType(Cursor.DEFAULT);
		}
		ensureCursorType(Cursor.CROSSHAIR);
		updateLimits(e.getX(), e.getY());
	}

	private void updateLimits(double xView, double yView) {
		var viewer = getViewer();
		if (viewer == null || viewer.getImageData() == null) {
			brushLimits.setVisible(false);
			return;
		}
		brushLimits.setVisible(true);
		brushLimits.setCenter(xView, yView);
		double radius = getBrushDiameter() / 2.0 / viewer.getDownsampleFactor();
		brushLimits.setRadius(radius);
	}
	
	
	@Override
	public void mouseDragged(MouseEvent e) {
		// Note: if the 'freehand' part of the polygon creation isn't desired, just comment out this whole method
		super.mouseDragged(e);

		updateLimitsAndCursor(e);
		if (!e.isPrimaryButtonDown()) {
            return;
        }
		
		// Can only modify annotations
		var viewer = getViewer();
		PathObject pathObject = viewer.getSelectedObject();
		if (pathObject == null || !pathObject.isAnnotation() || !pathObject.isEditable())
			return;
		if (pathObject != currentObject) {
			logger.debug("Selected object has changed from {} to {}", currentObject, pathObject);
			return;
		}

		ROI currentROI = pathObject.getROI();
		if (currentROI == null)
			return;

        PathObject pathObjectUpdated = getUpdatedObject(e, currentROI, pathObject);

		if (pathObject != pathObjectUpdated) {
			viewer.setSelectedObject(pathObjectUpdated, PathPrefs.selectionModeStatus().get());
		} else {
			viewer.repaint();
		}
	}
	
	
	protected boolean isSubtractMode(MouseEvent e) {
		PenInputManager manager = QuPathPenManager.getPenManager();
		if (manager.isEraser())
			return true;
		return e != null && (e.isAltDown() && !PathPrefs.selectionModeStatus().get());
	}
	
	
	private PathObject getUpdatedObject(MouseEvent e, ROI shapeROI, PathObject currentObject) {
		boolean pixelSnapping = requestPixelSnapping();
		Point2D p = mouseLocationToImage(e, false, pixelSnapping);

		// Don't do anything if outside the image bounds
		if (p.getX() < 0 || p.getY() < 0 || p.getX() >= getViewer().getServerWidth() || p.getY() >= getViewer().getServerHeight())
			return currentObject;

		// If we use pixel snapping, we get integer coordinates -
		// we actually want the *center*, so need to offset by 0.5
		if (pixelSnapping)
			p.setLocation(p.getX() + 0.5, p.getY() + 0.5);

		var viewer = getViewer();
		ImagePlane plane = shapeROI == null ? ImagePlane.getPlane(viewer.getZPosition(), viewer.getTPosition()) : shapeROI.getImagePlane();
		Geometry shapeNew;
		boolean subtractMode = isSubtractMode(e);
		Geometry shapeCurrent = shapeROI == null ? null : shapeROI.getGeometry();
		
		Geometry shapeDrawn = createShape(e, p.getX(), p.getY(),
				PathPrefs.useTileBrushProperty().get() && !e.isShiftDown(),
				subtractMode ? null : shapeCurrent);
		
		if (shapeDrawn == null)
			return currentObject;
		
		// Do our pixel snapping now, with the simpler geometry (rather than latter when things are already complex)
		if (requestPixelSnapping())
			shapeDrawn = GeometryTools.roundCoordinates(shapeDrawn);
		
		// Make sure we don't have any linestrings/points
		shapeDrawn = GeometryTools.ensurePolygonal(shapeDrawn);
		
		lastPoint = p;
		try {
			if (shapeROI != null) {
				// Check to see if any changes are required at all
				if (shapeDrawn == null || (subtractMode && !shapeCurrent.intersects(shapeDrawn)) || 
						(!subtractMode && shapeCurrent.covers(shapeDrawn)))
					return currentObject;
				
				// TODO: Consider whether a preference should be used rather than the shift key?
				// Anyhow, this will switch to 'dodge' mode, and avoid overlapping existing annotations
				boolean avoidOtherAnnotations = requestParentClipping(e);
				if (subtractMode) {
					// If subtracting... then just subtract
					shapeNew = shapeROI.getGeometry().difference(shapeDrawn);
				} else if (avoidOtherAnnotations) {
					shapeNew = shapeCurrent.union(shapeDrawn);
					shapeNew = refineGeometryByParent(shapeNew);
				} else {
					// Just add, regardless of whether there are other annotations below or not
					var temp = shapeROI.getGeometry();
					try {
						shapeNew = temp.union(shapeDrawn);
					} catch (Exception e2) {
						shapeNew = shapeROI.getGeometry();
					}
				}
			} else {
				shapeNew = shapeDrawn;
			}
			
			// If we aren't snapping, at least remove some vertices
			if (!requestPixelSnapping()) {
				try {
					shapeNew = VWSimplifier.simplify(shapeNew, 0.1);
				} catch (Exception e2) {
                    logger.error("Error simplifying ROI: {}", e2.getMessage(), e2);
				}
			}
			
			// Make sure we fit inside the image
			shapeNew = GeometryTools.constrainToBounds(shapeNew, 0, 0, viewer.getServerWidth(), viewer.getServerHeight());

			// Sometimes we can end up with a GeometryCollection containing lines/non-areas... if so, remove these
			if (shapeNew instanceof GeometryCollection) {
				shapeNew = GeometryTools.ensurePolygonal(shapeNew);
			}
					
			ROI roiNew = GeometryTools.geometryToROI(shapeNew, plane);
			
			if (currentObject instanceof PathAnnotationObject) {
				((PathAnnotationObject)currentObject).setROI(roiNew);
				return currentObject;
			}
			
//			shapeNew = new PathAreaROI(new Area(shapeNew.getShape()));
			PathObject pathObjectNew = PathObjects.createAnnotationObject(roiNew, PathPrefs.autoSetAnnotationClassProperty().get());
			if (currentObject != null) {
				pathObjectNew.setName(currentObject.getName());
				pathObjectNew.setColor(currentObject.getColor());
				pathObjectNew.setPathClass(currentObject.getPathClass());
			}
			return pathObjectNew;

		} catch (Exception ex) {
			logger.error("Error updating ROI", ex);
			return currentObject;
		}
	}
	
	
	@Override
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);

		updateLimitsAndCursor(e);

		if (e.isConsumed())
			return;

		if (currentObject != null) {
			commitObjectToHierarchy(e, currentObject);
		}

		lastPoint = null;
		this.currentObject = null;

		resetConstrainingObjects();
	}

	
	protected double getBrushDiameter() {
		PenInputManager manager = QuPathPenManager.getPenManager();
		double scale = manager.getPressure();
		var viewer = getViewer();
		double brushDiameter = PathPrefs.brushDiameterProperty().get() * scale;
		if (PathPrefs.brushScaleByMagProperty().get())
			return brushDiameter * viewer.getDownsampleFactor();
		else
			return brushDiameter;
	}
	
	
	/**
	 * Create a new Geometry using the specified tool, assuming a user click/drag at the provided x &amp; y coordinates.
	 * @param e mouse event, used to query modifiers
	 * @param x x-coordinate of the center of the new shape, in the image space
	 * @param y y-coordinate of the center of the new shape, in the image space
	 * @param useTiles If true, request generating a shape from existing tile objects.
	 * @param addToShape If provided, it can be assumed that any new shape ought to be added to this one.
	 *                   The purpose is that this method may (optionally) use the shape to refine the one it will generate, 
	 *                   e.g. to avoid having isolated or jagged boundaries.
	 * @return a new geometry corresponding to the shape that should be added to the ROI
	 */
	protected Geometry createShape(MouseEvent e, double x, double y, boolean useTiles, Geometry addToShape) {
		
		// See if we're on top of a tile
		if (useTiles) {
			List<PathObject> listSelectable = ToolUtils.getSelectableObjectList(getViewer(), x, y);
			for (PathObject temp : listSelectable) {
//				if ((temp instanceof PathDetectionObject) && temp.getROI() instanceof PathArea)
				if (temp instanceof PathTileObject && temp.hasROI() && temp.getROI().isArea() && !(temp.getROI() instanceof RectangleROI)) {
					creatingTiledROI = true;
					return temp.getROI().getGeometry();
				}
			}
			// If we're currently creating a tiled, ROI, but now not clicked on a tile, just return
			if (creatingTiledROI)
				return null;
		}
		
		// Compute a diameter scaled according to the pressure being applied
		double diameter = Math.max(1, getBrushDiameter());
		
		Geometry geometry;
		if (lastPoint == null) {
			var shapeFactory = new GeometricShapeFactory(getGeometryFactory());
			shapeFactory.setCentre(new Coordinate(x, y));
			shapeFactory.setSize(diameter);
//			shapeFactory.setCentre(new Coordinate(x-diameter/2, y-diameter/2));
			geometry = shapeFactory.createEllipse();
		} else {
			if (lastPoint.distanceSq(x, y) == 0)
				return null;
			var factory = getGeometryFactory();
			geometry = factory.createLineString(new Coordinate[] {
					new Coordinate(lastPoint.getX(), lastPoint.getY()),
					new Coordinate(x, y)}).buffer(diameter/2.0);
		}
		
		return geometry;
	}
	
	private boolean creatingTiledROI = false;
	
	protected GeometryFactory getGeometryFactory() {
        return GeometryTools.getDefaultFactory();
	}

	@Override
	protected ROI createNewROI(MouseEvent e, double x, double y, ImagePlane plane) {
		creatingTiledROI = false;
		lastPoint = null;
		Geometry geom = createShape(e, x, y, PathPrefs.useTileBrushProperty().get(), null);
		if (geom == null || geom.isEmpty())
			return ROIs.createEmptyROI();
		var viewer = getViewer();
		geom = GeometryTools.roundCoordinates(geom);
		geom = GeometryTools.constrainToBounds(geom, 0, 0, viewer.getServerWidth(), viewer.getServerHeight());
		return GeometryTools.geometryToROI(geom, plane);
	}

	@Override
	public void handlerAdded(QuPathViewer viewer) {
		if (viewer != null) {
			var view = viewer.getView();
			if (!view.getChildren().contains(brushLimits)) {
				view.getChildren().add(brushLimits);
				viewer.addViewerListener(listener);

				// Make invisible before further mouse events, because the coordinates are unknown
				brushLimits.setVisible(false);
				updateLimitsAndCursor();
			}
		}
	}

	@Override
	public void handlerRemoved(QuPathViewer viewer) {
		if (viewer != null) {
			var view = viewer.getView();
			view.getChildren().remove(brushLimits);
			viewer.removeViewerListener(listener);
			brushLimits.setVisible(false);
		}
	}

	/**
	 * Get the node used to display the brush limits in the viewer.
	 * This is typically to enable styling to be adjusted using CSS.
	 * @return the brush limits
	 */
	protected BrushLimits getBrushLimits() {
		return brushLimits;
	}

	private static final KeyCombination comboFill = new KeyCodeCombination(KeyCode.F);

	@Override
	protected void handleKeyEvent(KeyEvent e) {
		super.handleKeyEvent(e);
		if (currentObject == null) {
			// Don't consume events if we aren't drawing
			return;
		}
		if (e.getEventType() == KeyEvent.KEY_RELEASED && comboFill.match(e)) {
			// Fill the existing object
			var roi = currentObject.getROI();
			var roiFilled = RoiTools.fillHoles(roi);
			if (!roi.equals(roiFilled)) {
				currentObject = PathObjectTools.createLike(currentObject, roiFilled);
				var viewer = getViewer();
				viewer.setSelectedObject(this.currentObject);
				viewer.getROIEditor().setROI(null);
			}
		}
		e.consume();
	}

	private class ViewerListener implements QuPathViewerListener {

		@Override
		public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
			updateLimitsAndCursor();
		}

		@Override
		public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
			updateLimitsAndCursor();
		}

		@Override
		public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			updateLimitsAndCursor();
		}
	}

}
