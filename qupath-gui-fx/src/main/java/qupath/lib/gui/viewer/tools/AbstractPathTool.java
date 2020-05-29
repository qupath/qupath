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

package qupath.lib.gui.viewer.tools;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Abstract implementation of a PathTool.
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractPathTool implements EventHandler<MouseEvent> {
	
	private final static Logger logger = LoggerFactory.getLogger(AbstractPathTool.class);

//	private QuPathViewer viewer;
	
	/**
	 * Parent object that may be used to constrain ROIs, if required.
	 */
	private PathObject constrainedParentObject;
	/**
	 * Geometry of the parent object ROI used to constrain ROIs, if required.
	 */
	private Geometry constrainedParentGeometry;
	/**
	 * Collection of Geometry objects that may be subtracted if drawing a constrained ROI.
	 */
	private Collection<Geometry> constrainedRemoveGeometries;
	/**
	 * The starting point for a ROI drawn in 'constrained' mode.
	 * ROIs containing this point should not be used for 'subtraction'.
	 */
	private Point2 constrainedStartPoint;
	
	void ensureCursorType(Cursor cursor) {
		// We don't want to change a waiting cursor unnecessarily
		var viewer = getViewer();
		Cursor currentCursor = viewer.getCursor();
		if (currentCursor == null || currentCursor == Cursor.WAIT)
			return;
		viewer.setCursor(cursor);
	}
	
	/**
	 * Returns true if the tool requests that pixel coordinates be snapped to integer values.
	 * Default returns true.
	 * 
	 * @return
	 */
	protected boolean requestPixelSnapping() {
		return PathPrefs.usePixelSnappingProperty().get();
	}
	
	protected QuPathViewer getViewer() {
		return QuPathGUI.getInstance().getViewer();
//		return viewer;
	}
	
	protected Point2D mouseLocationToImage(MouseEvent e, boolean constrainToBounds, boolean snapToPixel) {
		var viewer = getViewer();
		var p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, constrainToBounds);
		if (snapToPixel)
			p.setLocation(Math.floor(p.getX()), Math.floor(p.getY()));
		return p;
	}

	
	/**
	 * Query whether parent clipping should be applied.
	 * 
	 * <p>This might depend upon the MouseEvent.
	 * 
	 * @param e
	 * @return
	 */
	boolean requestParentClipping(MouseEvent e) {
		return PathPrefs.clipROIsForHierarchyProperty().get() != (e.isShiftDown() && e.isShortcutDown());
	}
	
	
	/**
	 * Apply clipping based on the current parent object.
	 * <p>
	 * Returns an empty ROI if this result of the clipping is an empty area.
	 * 
	 * @param currentROI
	 * @return
	 */
	ROI refineROIByParent(ROI currentROI) {
		// Don't do anything with lines
		if (currentROI.isLine())
			return currentROI;
		// Handle areas
		var geometry = currentROI.getGeometry();
		geometry = refineGeometryByParent(geometry);
		if (geometry.isEmpty())
			return ROIs.createEmptyROI();
		else
			return GeometryTools.geometryToROI(geometry, currentROI.getImagePlane());
	}
	
	Geometry refineGeometryByParent(Geometry geometry) {
		return refineGeometryByParent(geometry, true);
	}
	
	private Geometry refineGeometryByParent(Geometry geometry, boolean tryAgain) {
		try {
			if (constrainedParentGeometry != null)
				geometry = geometry.intersection(constrainedParentGeometry);
			int count = 0;
			var constrainedRemoveGeometries = getConstrainedRemoveGeometries();
			if (!constrainedRemoveGeometries.isEmpty()) {
				var envelope = geometry.getEnvelopeInternal();
				for (var temp : constrainedRemoveGeometries) {
					// Note: the relate operation tests for interior intersections, but can be very slow
					// Ideally, we would reduce these tests
					if (envelope.intersects(temp.getEnvelopeInternal()) && geometry.relate(temp, "T********")) {
						geometry = geometry.difference(temp);
						envelope = geometry.getEnvelopeInternal();
						count++;
					}
				}
			}
			logger.debug("Clipped ROI with {} geometries", count);
		} catch (Exception e) {
			if (tryAgain) {
				logger.warn("First Error refining ROI, will retry after buffer(0): {}", e.getLocalizedMessage());
				return refineGeometryByParent(geometry.buffer(0.0), false);
			}
			logger.warn("Error refining ROI: {}", e.getLocalizedMessage());
			logger.debug("", e);
		}
		return geometry;
	}
	
	
	/**
	 * Set the parent that may be used to constrain a new ROI, if possible.
	 * 
	 * @param hierarchy object hierarchy containing potential constraining objects
	 * @param xx x-coordinate in the image space of the starting point for the new object
	 * @param yy y-coordinate in the image space of the starting point for the new object
	 * @param exclusions objects not to consider (e.g. the new ROI being created)
	 */
	void setConstrainedAreaParent(final PathObjectHierarchy hierarchy, double xx, double yy, Collection<PathObject> exclusions) {
		if (!Platform.isFxApplicationThread())
			throw new IllegalStateException("Not on the FxApplication thread!");

		// Reset parent area & its descendant annotation areas
		constrainedParentGeometry = null;
		constrainedRemoveGeometries = null;
		
		// Identify the smallest area annotation that contains the specified point
		constrainedParentObject = getSelectableObjectList(xx, yy)
				.stream()
				.filter(p -> !p.isDetection() && p.hasROI() && p.getROI().isArea() && !exclusions.contains(p))
				.sorted(Comparator.comparing(p -> p.getROI().getArea()))
				.findFirst()
				.orElseGet(() -> null);
				
//		if (constrainedAreaParent == null)
//			return;
		
		// Check the parent is a valid potential parent
		if (constrainedParentObject == null || !(constrainedParentObject.hasROI() && constrainedParentObject.getROI().isArea())) {
			constrainedParentObject = hierarchy.getRootObject();
		}
		
		// Get the parent Geometry
		if (constrainedParentObject.hasROI() && constrainedParentObject.getROI().isArea())
			constrainedParentGeometry = constrainedParentObject.getROI().getGeometry();
		
		constrainedStartPoint = new Point2(xx, yy);
	}
	
	
	Collection<Geometry> getConstrainedRemoveGeometries() {
		if (!Platform.isFxApplicationThread())
			throw new IllegalStateException("Not on the FxApplication thread!");
		
		if (constrainedRemoveGeometries == null) {
			
			var viewer = getViewer();
			if (viewer == null)
				return Collections.emptyList();
			
			var hierarchy = viewer.getHierarchy();			
			if (hierarchy == null || constrainedParentObject == null)
				return Collections.emptyList();
			
			var selected = viewer.getSelectedObject();
			
			// Figure out what needs to be subtracted
			boolean fullImage = hierarchy.getRootObject() == constrainedParentObject;
			Collection<PathObject> toRemove;
			if (fullImage)
				toRemove = hierarchy.getAnnotationObjects();
			else
				toRemove = hierarchy.getObjectsForRegion(PathAnnotationObject.class, ImageRegion.createInstance(constrainedParentObject.getROI()), null);
			
			logger.debug("Constrained ROI drawing: identifying objects to remove");
	
			Envelope boundsEnvelope = constrainedParentGeometry == null ? null : constrainedParentGeometry.getEnvelopeInternal();
			constrainedRemoveGeometries = new ArrayList<>();
			for (PathObject child : toRemove) {
				if (child.isDetection() || child == constrainedParentObject|| !child.hasROI() || 
						!child.getROI().isArea() || child == selected ||
						(constrainedStartPoint != null && child.getROI().contains(constrainedStartPoint.getX(), constrainedStartPoint.getY())))
					continue;
				Geometry childArea = child.getROI().getGeometry();
				Envelope childEnvelope = childArea.getEnvelopeInternal();
				// Quickly filter out objects that don't intersect with the bounds, or which entirely cover it
				if (constrainedParentGeometry != null &&
						(!boundsEnvelope.intersects(childEnvelope) || 
								(childEnvelope.covers(boundsEnvelope) && childArea.covers(constrainedParentGeometry))))
					continue;
				constrainedRemoveGeometries.add(childArea);
			}
		}
		return constrainedRemoveGeometries;
	}
	
	synchronized void resetConstrainedAreaParent() {
		this.constrainedParentObject = null;
		this.constrainedParentGeometry = null;
		this.constrainedRemoveGeometries = null;
	}
	
	
	synchronized PathObject getCurrentParent() {
		return constrainedParentObject;
	}
	
	/**
	 * When drawing a constrained ROI, get a Geometry defining the outer limits.
	 * @return
	 */
	synchronized Geometry getConstrainedAreaBounds() {
		return constrainedParentGeometry;
	}
	
//	/**
//	 * When drawing a constrained ROI, get a Geometry defining the inner area that should be 'subtracted'.
//	 * @return
//	 */
//	synchronized Geometry getConstrainedAreaToSubtract() {
//		return constrainedAreaToRemove;
//	}
	
	
	
	/**
	 * Try to select an object with a ROI overlapping a specified coordinate.
	 * If there is no object found, the current selected object will be reset (to null).
	 * 
	 * @param x
	 * @param y
	 * @param searchCount - how far up the hierarchy to go, i.e. how many parents to check if objects overlap
	 * @return true if any object was selected
	 */
	boolean tryToSelect(double x, double y, int searchCount, boolean addToSelection) {
		return tryToSelect(x, y, searchCount, addToSelection, false);
	}
	
	boolean tryToSelect(double x, double y, int searchCount, boolean addToSelection, boolean toggleSelection) {
		var viewer = getViewer();
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return false;
		PathObject pathObject = getSelectableObject(x, y, searchCount);
		if (toggleSelection && hierarchy.getSelectionModel().getSelectedObject() == pathObject)
			hierarchy.getSelectionModel().deselectObject(pathObject);
		else
			viewer.setSelectedObject(pathObject, addToSelection);
		// Reset selection if we have nothing
		if (pathObject == null && addToSelection)
			viewer.setSelectedObject(null);
		return pathObject != null;
	}
	
	
	/**
	 * Determine which object would be selected by a click in this location - but do not actually apply the selection.
	 * 
	 * @param x
	 * @param y
	 * @param searchCount - how far up the hierarchy to go, i.e. how many parents to check if objects overlap
	 * @return true if any object was selected
	 */
	PathObject getSelectableObject(double x, double y, int searchCount) {
		List<PathObject> pathObjectList = getSelectableObjectList(x, y);
		if (pathObjectList == null || pathObjectList.isEmpty())
			return null;
//		int ind = pathObjectList.size() - searchCount % pathObjectList.size() - 1;
		int ind = searchCount % pathObjectList.size();
		return pathObjectList.get(ind);
	}
	
	
	/**
	 * Get a list of all selectable objects overlapping the specified x, y coordinates, ordered by depth in the hierarchy
	 * @param x
	 * @param y
	 * @return
	 */
	List<PathObject> getSelectableObjectList(double x, double y) {
		var viewer = getViewer();
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		
		Collection<PathObject> pathObjects = PathObjectTools.getObjectsForLocation(
				hierarchy, x, y, viewer.getZPosition(), viewer.getTPosition(), viewer.getMaxROIHandleSize());
		if (pathObjects.isEmpty())
			return Collections.emptyList();
		List<PathObject> pathObjectList = new ArrayList<>(pathObjects);
		if (pathObjectList.size() == 1)
			return pathObjectList;
		Collections.sort(pathObjectList, PathObjectHierarchy.HIERARCHY_COMPARATOR);
		return pathObjectList;
	}
	
	
	
	public void mouseClicked(MouseEvent e) {}

	public void mouseDragged(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) {}

	public void mouseExited(MouseEvent e) {}
	
	public void mouseMoved(MouseEvent e) {}

	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger()) {
			e.consume();
			return;
		}
		
		Object source = e.getSource();
		if (source instanceof Node) {
			Node node = (Node)source;
			if (node.isFocusTraversable() && !node.isFocused()) {
				node.requestFocus();
				e.consume();
			}
		}
	}

	public void mouseReleased(MouseEvent e) {
		if (e.isPopupTrigger()) {
			e.consume();
			return;
		}
	}

	@Override
	public void handle(MouseEvent event) {
		var type = event.getEventType();
		if (type == MouseEvent.DRAG_DETECTED || type == MouseEvent.MOUSE_DRAGGED)
			mouseDragged(event);
		else if (type == MouseEvent.MOUSE_CLICKED)
			mouseClicked(event);
		else if (type == MouseEvent.MOUSE_MOVED)
			mouseMoved(event);
		else if (type == MouseEvent.MOUSE_PRESSED)
			mousePressed(event);
		else if (type == MouseEvent.MOUSE_RELEASED)
			mouseReleased(event);
		else if (type == MouseEvent.MOUSE_ENTERED)
			mouseEntered(event);
		else if (type == MouseEvent.MOUSE_EXITED)
			mouseExited(event);
	}
	
	
}
