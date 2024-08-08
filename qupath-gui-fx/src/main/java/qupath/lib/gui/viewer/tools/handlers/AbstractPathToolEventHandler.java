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

package qupath.lib.gui.viewer.tools.handlers;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

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
abstract class AbstractPathToolEventHandler implements EventHandler<MouseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractPathToolEventHandler.class);

	private ConstrainingObjects constrainingObjects = new ConstrainingObjects();

	/**
	 * Ensure that the specified cursor is set in the current viewer.
	 * @param cursor
	 */
	protected void ensureCursorType(Cursor cursor) {
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
	protected  boolean requestParentClipping(MouseEvent e) {
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
	protected ROI refineROIByParent(ROI currentROI) {
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
	
	protected Geometry refineGeometryByParent(Geometry geometry) {
		return constrainingObjects.refineGeometryByParent(geometry, true);
	}


	/**
	 * New annotations can be constrained while they are being drawn, to avoid overlaps with existing annotations
	 * or to ensure they are drawn within a parent annotation.
	 * <p>
	 * This method requests that the constraining objects are identified now based upon the specified mouse position.
	 * It is useful when starting to draw with a tool that makes use of constraining objects.
	 *
	 * @param viewer the viewer that may contain potential constraining objects
	 * @param xx x-coordinate in the image space of the starting point for the new object
	 * @param yy y-coordinate in the image space of the starting point for the new object
	 * @param exclusions objects not to consider (e.g. the new ROI being created)
	 */
	protected void updatingConstrainingObjects(final QuPathViewer viewer, double xx, double yy, Collection<PathObject> exclusions) {
		if (!Platform.isFxApplicationThread())
			throw new IllegalStateException("Not on the FxApplication thread!");

		// Reset parent area & its descendant annotation areas
		constrainingObjects.reset();

		// Check we have a hierarchy to work with
		var hierarchy = viewer == null ? null : viewer.getHierarchy();
		if (hierarchy == null)
			return;

		// Identify the smallest area annotation that contains the specified point
		var constrainedParentObject = ToolUtils.getSelectableObjectList(viewer, xx, yy)
                .stream()
                .filter(p -> !p.isDetection() && p.hasROI() && p.getROI().isArea() && !exclusions.contains(p))
				.min(Comparator.comparing(p -> p.getROI().getArea()))
				.orElse(null);
				
		// Check the parent is a valid potential parent
		if (constrainedParentObject == null || !(constrainedParentObject.hasROI() && constrainedParentObject.getROI().isArea())) {
			constrainedParentObject = hierarchy.getRootObject();
		}
		
		// Update the constraining objects
		constrainingObjects.update(viewer, constrainedParentObject, xx, yy);
	}

	/**
	 * Reset the constraining objects.
	 * These should be done as soon as they are no longer required, to prevent a memory leak by inadvertently
	 * holding on to an object hierarchy too long.
	 */
	protected synchronized void resetConstrainingObjects() {
		constrainingObjects.reset();
	}
	
	
	protected synchronized PathObject getCurrentParent() {
		return constrainingObjects.getConstrainedParentObject();
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
		if (source instanceof Node node) {
			if (node.isFocusTraversable() && !node.isFocused()) {
				node.requestFocus();
				e.consume();
			}
		}
	}

	public void mouseReleased(MouseEvent e) {
		if (e.isPopupTrigger()) {
			e.consume();
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


	private static class ConstrainingObjects {

		private QuPathViewer viewer;

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

		public void reset() {
			constrainedParentObject = null;
			constrainedParentGeometry = null;
			constrainedRemoveGeometries = null;
			constrainedStartPoint = null;
		}

		public void update(QuPathViewer viewer, PathObject parent, double xStart, double yStart) {
			this.viewer = viewer;
			this.constrainedParentObject = parent;
			if (parent != null && parent.hasROI() && parent.getROI().isArea()) {
				this.constrainedParentGeometry = parent.getROI().getGeometry();
			} else {
				this.constrainedParentGeometry = null;
			}
			this.constrainedRemoveGeometries = null; // Compute lazily
			constrainedStartPoint = new Point2(xStart, yStart);
		}

		public PathObject getConstrainedParentObject() {
			return constrainedParentObject;
		}

		public Geometry getConstrainedParentGeometry() {
			return constrainedParentGeometry;
		}

		public Collection<Geometry> getConstrainedRemoveGeometries() {
			if (!Platform.isFxApplicationThread())
				throw new IllegalStateException("Not on the FxApplication thread!");

			if (constrainedRemoveGeometries == null) {
				var hierarchy = viewer == null ? null : viewer.getHierarchy();
				if (hierarchy == null || constrainedParentObject == null)
					return Collections.emptyList();

				var selected = hierarchy.getSelectionModel().getSelectedObject();

				// Figure out what needs to be subtracted
				boolean fullImage = hierarchy.getRootObject() == constrainedParentObject;
				Collection<PathObject> toRemove;
				if (fullImage)
					toRemove = hierarchy.getAnnotationObjects();
				else
					toRemove = hierarchy.getAnnotationsForRegion(ImageRegion.createInstance(constrainedParentObject.getROI()), null);

				logger.debug("Constrained ROI drawing: identifying objects to remove");

				Envelope boundsEnvelope = constrainedParentGeometry == null ? null : constrainedParentGeometry.getEnvelopeInternal();
				constrainedRemoveGeometries = new ArrayList<>();
				for (PathObject child : toRemove) {
					if (child.isDetection() || child == constrainedParentObject|| !child.hasROI() ||
							!child.getROI().isArea() || child.getROI().getZ() != viewer.getZPosition() || child.getROI().getT() != viewer.getTPosition() ||
							child == selected ||	(constrainedStartPoint != null && child.getROI().contains(constrainedStartPoint.getX(), constrainedStartPoint.getY())))
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

		public Point2 getConstrainedStartPoint() {
			return constrainedStartPoint;
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

	}

	
}
