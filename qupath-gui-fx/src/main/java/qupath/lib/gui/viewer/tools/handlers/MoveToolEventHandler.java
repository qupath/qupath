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
import java.awt.geom.Rectangle2D;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.Animation;
import javafx.animation.Animation.Status;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;

/**
 * The MoveTool is used for quite a lot of things, movement-related:
 * movement around an image (panning), moving ROIs (translating) and moving individual
 * 'handles' of ROIs (resizing/reshaping)
 * 
 * @author Pete Bankhead
 *
 */
public class MoveToolEventHandler extends AbstractPathToolEventHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(MoveToolEventHandler.class);

	private static boolean requestDynamicDragging = true;
	
	private Point2D pDragging;
	private double dx, dy; // Last dragging displacements
	private long lastDragTimestamp; // Used to determine if the user has stopped dragging (but may not yet have release the mouse button)
	
	private ViewerMover mover;	
	
	@Override
	public void mousePressed(MouseEvent e) {
		
		if (mover != null)
			mover.stopMoving();
		
		super.mousePressed(e);
		
		if (!e.isPrimaryButtonDown() || e.isConsumed())
            return;
		
		var viewer = getViewer();
		
		boolean snapping = false;
		Point2D p = mouseLocationToImage(e, false, snapping);
		double xx = p.getX();
		double yy = p.getY();
		
		// If we are double-clicking, see if we can access a ROI
		// TODO: Consider whether Alt & the Shortcut key should both have the same effect
		if (e.getClickCount() > 1 || e.isAltDown() || e.isShortcutDown()) {
			boolean selected = false;
			if (e.isAltDown() || e.isShortcutDown())
				selected = ToolUtils.tryToSelect(viewer, xx, yy, e.getClickCount()-1, true, true);
			else
				selected = ToolUtils.tryToSelect(viewer, xx, yy, e.getClickCount()-2, false);
			e.consume();
			pDragging = null;
			if (!selected && PathPrefs.doubleClickToZoomProperty().get()) {
				double downsample = viewer.getDownsampleFactor();
				if (e.isAltDown() || e.isShortcutDown())
					downsample *= 2;
				else
					downsample /= 2;
				viewer.setDownsampleFactor(downsample, e.getX(), e.getY());
			}
			return;
		}
		
		if (!viewer.isSpaceDown() && viewer.getHierarchy() != null) {
			
			// Set the current parent object based on the first click
			PathObject currentObject = viewer.getSelectedObject();
			updatingConstrainingObjects(viewer, xx, yy, Collections.singleton(currentObject));
			
			// See if we can get a handle to edit the ROI
			// Don't want to edit detections / TMA cores
			ROI currentROI = viewer.getCurrentROI();
			RoiEditor editor = viewer.getROIEditor();
			// Try dealing with having a ROI first
			if (currentROI != null) {
				if (editor.getROI() == currentROI) {
					// 1.5 increases the range; the handle radius alone is too small a distance, especially if the handles are painted as squares -
					// because 1.5 >~ sqrt(2) it ensures that at least the entire square is 'active' (and a bit beyond it)
					double search = viewer.getMaxROIHandleSize() * 0.75;
					if (editor.grabHandle(xx, yy, search, e.isShiftDown()))
						e.consume();
				}
				if (!e.isConsumed() && canAdjust(currentObject) &&
						(RoiTools.areaContains(currentROI, xx, yy) || ToolUtils.getSelectableObjectList(viewer, xx, yy).contains(currentObject))) {
					// If we have a translatable ROI, try starting translation
					// No translation should happen if we have points, because we never want to move all points together
					if (!editor.getROI().isPoint() && editor.startTranslation(xx, yy, PathPrefs.usePixelSnappingProperty().get() && currentROI.isArea()))
						e.consume();
				}
				if (e.isConsumed()) {
					pDragging = null;
					return;
				}
			}
		}
		
		// Store point for drag-to-pan
        pDragging = p;
//        viewer.setDoFasterRepaint(true); // Turn on if dragging is too slow
	}
	
	private static boolean canAdjust(PathObject pathObject) {
		return (pathObject != null && pathObject.isEditable());
	}
	
	
	@Override
	public void mouseDragged(MouseEvent e) {
		
		if (mover != null)
			mover.stopMoving();
		
		super.mouseDragged(e);
		
		if (!e.isPrimaryButtonDown() || e.isConsumed())
            return;

		// Handle ROIs if the spacebar isn't down
		var viewer = getViewer();
		if (!viewer.isSpaceDown()) {
			
			RoiEditor editor = viewer.getROIEditor();
			Point2D p = mouseLocationToImage(e, true, false);

			// Try moving handle
			if (editor != null && editor.hasActiveHandle()) {
				double x = p.getX();
				double y = p.getY();
				if (PathPrefs.usePixelSnappingProperty().get() && editor.getROI() != null && editor.getROI().isArea()) {
					x = (int)x;
					y = (int)y;
				}
				ROI updatedROI = editor.setActiveHandlePosition(x, y, viewer.getDownsampleFactor()/2.0, e.isShiftDown());
				if (updatedROI == null)
					// This shouldn't occur...?
					logger.warn("Updated ROI is null! Will be skipped...");
				else {
					PathObject selectedObject = viewer.getSelectedObject();
					if (selectedObject.getROI() != updatedROI && selectedObject instanceof PathROIObject)
						((PathROIObject)selectedObject).setROI(updatedROI);
					viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(selectedObject), true); // TODO: Check event firing frequency!
//					viewer.repaint();
					e.consume();
					return;
				}
			}
			
			// Try to translate, if that's what is happening
			ROI currentROI = viewer.getCurrentROI();
			if (editor != null && editor.isTranslating()) {
				Rectangle2D boundsBefore = AwtTools.getBounds2D(currentROI);
				ROI translatedROI = editor.updateTranslation(p.getX(), p.getY(), viewer.getServerBounds());
				if (translatedROI != null) {
					Rectangle2D boundsAfter = AwtTools.getBounds2D(currentROI);
					Rectangle2D boundsIntersection = new Rectangle2D.Double();
					Rectangle2D.union(boundsBefore, boundsAfter, boundsIntersection);
					
					((PathROIObject)viewer.getSelectedObject()).setROI(translatedROI);
					
					viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(viewer.getSelectedObject()), true);				
				}
				pDragging = null;
				return;
			}
			
			// Try to select objects, if alt is down
			if (e.isAltDown()) {
				ToolUtils.tryToSelect(viewer, p.getX(), p.getY(), e.getClickCount()-1, true, false);
				e.consume();
				return;
			}
		}
		
		// If we don't have a previous point, we aren't dragging (e.g. there was an alt-click)
		if (pDragging == null)
			return;
		
		// Extract previous coordinates so we can reuse the Point2D object
		double xPrevious = pDragging.getX();
		double yPrevious = pDragging.getY();
		
		// Calculate how much the image was dragged
		pDragging = mouseLocationToImage(e, false, false);
		dx = pDragging.getX() - xPrevious;
		dy = pDragging.getY() - yPrevious;

		// Update the viewer
		viewer.setDoFasterRepaint(true);
		viewer.setCenterPixelLocation(viewer.getCenterPixelX() - dx, viewer.getCenterPixelY() - dy);
//		viewer.setDoFasterRepaint(false);
		pDragging = mouseLocationToImage(e, false, false);
		lastDragTimestamp = System.currentTimeMillis();
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
		if (e.isConsumed())
			return;
		
		var viewer = getViewer();
		RoiEditor editor = viewer.getROIEditor();
		if (editor != null && (editor.hasActiveHandle() || editor.isTranslating())) {
			boolean roiChanged = (editor.isTranslating() && editor.finishTranslation()) || editor.hasActiveHandle();
			editor.resetActiveHandle();
//			if (editor.isTranslating())
//				editor.finishTranslation();
			e.consume();
			PathObject pathObject = viewer.getSelectedObject();
			
			if (requestParentClipping(e) && pathObject instanceof PathAnnotationObject) {
				ROI roiNew = refineROIByParent(pathObject.getROI());
				((PathAnnotationObject)pathObject).setROI(roiNew);
			}
			
			if (pathObject != null && pathObject.hasROI() && pathObject.getROI().isEmpty()) {
				if (pathObject.getParent() != null)
					viewer.getHierarchy().removeObject(pathObject, true);
				viewer.setSelectedObject(null);
			} else {
				PathObjectHierarchy hierarchy = viewer.getHierarchy();
				if (pathObject instanceof TMACoreObject) {
					hierarchy.fireHierarchyChangedEvent(pathObject);
				} else if (pathObject != null) {
					// Handle ROI changes only if required
					if (roiChanged) {
						var updatedROI = editor.getROI();
						if (pathObject.getROI() != updatedROI && pathObject instanceof PathROIObject)
							((PathROIObject)pathObject).setROI(updatedROI);

						hierarchy.removeObjectWithoutUpdate(pathObject, true);
						if (getCurrentParent() == null || !PathPrefs.clipROIsForHierarchyProperty().get() || e.isShiftDown())
							hierarchy.addObject(pathObject);
						else
							hierarchy.addObjectBelowParent(getCurrentParent(), pathObject, true);
					}
				}
				viewer.setSelectedObject(pathObject);				
			}
		}
		
		
		// Optionally continue a dragging movement until the canvas comes to a standstill
		if (pDragging != null && requestDynamicDragging && System.currentTimeMillis() - lastDragTimestamp < 100 && (dx*dx + dy*dy > viewer.getDownsampleFactor())) {
			mover = new ViewerMover(viewer);
			mover.startMoving(dx, dy, false);
		} else
	        viewer.setDoFasterRepaint(false);
		
		
		// Make sure we don't have a previous point (to prevent weird dragging artefacts)
		pDragging = null;

		// Reset any constraining objects
		resetConstrainingObjects();
	}
	
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		
		// We don't want to change a waiting cursor unnecessarily
		var viewer = getViewer();
		Cursor cursorType = viewer.getCursor();
		if (cursorType == Cursor.WAIT)
			return;
		
		// If we are already translating, we must need a move cursor
		if (viewer.getROIEditor().isTranslating()) {
			if (cursorType != Cursor.MOVE)
				viewer.setCursor(Cursor.MOVE);
			return;
		}
		
		// Check if we should have a panning or moving cursor, changing if required
		ROI currentROI = viewer.getCurrentROI();
		if (currentROI != null && canAdjust(viewer.getSelectedObject())) {
			Point2D p2 = mouseLocationToImage(e, true, false);
			double xx = p2.getX();
			double yy = p2.getY();
			if (RoiTools.areaContains(currentROI, xx, yy)) {
				ensureCursorType(Cursor.MOVE);
				return;
			}
		}
		ensureCursorType(Cursor.HAND);
	}

	
	
	
	
	
	/**
	 * Helper class for panning a {@link QuPathViewer} (reasonably) smoothly.
	 */
	public static class ViewerMover {
		
		private QuPathViewer viewer;
		private Timeline timer; // Timeline used to continue dragging movements
		private long timestamp = -1;
		
		private int heartbeat = 10; // Requested heartbeat in ms... 10 is infeasibly fast?
		
		private double dx, dy; // Last dragging displacements
		private boolean constantVelocity = false;
		
		/**
		 * Constructor.
		 * @param viewer the viewer that will be controlled by this object
		 */
		public ViewerMover(final QuPathViewer viewer) {
			this.viewer = viewer;
		}

		void handleUpdate() {
//			if (timestamp < 0 || viewer == null || pDragging != null) {
			if (timestamp < 0 || viewer == null) {
				stopMoving();
				return;
			}
			long newTimestamp = System.currentTimeMillis();
			double scale = constantVelocity ? 1 : 1 - (newTimestamp - timestamp) * .005;
			// If too much time has elapsed, stop
			if (scale <= 0)
				return;
			
			timestamp = newTimestamp;
			dx *= scale;
			dy *= scale;
			double downsample = viewer.getDownsampleFactor();
			if (dx*dx + dy*dy < downsample*downsample*4) {
				stopMoving();
				return;
			}
			
			viewer.setCenterPixelLocation(viewer.getCenterPixelX() - dx, viewer.getCenterPixelY() - dy);
		}
		
		/**
		 * Start moving, with initial velocity given by dx and dy.
		 * @param dx
		 * @param dy
		 * @param constantVelocity 
		 */
		public void startMoving(final double dx, final double dy, final boolean constantVelocity) {
			this.dx = dx;
			this.dy = dy;
			this.constantVelocity = constantVelocity;
			if (timer == null) {
				timer = new Timeline(
						new KeyFrame(
								Duration.ZERO,
								actionEvent -> handleUpdate()
								),
						new KeyFrame(
								Duration.millis(heartbeat)
								)
						);
				timer.setCycleCount(Animation.INDEFINITE);
			}
//			timer.setCoalesce(true);
			timestamp = System.currentTimeMillis();
			timer.playFromStart();
		}
		
		/**
		 * Cancel either the x- or y-axis direction of the movement. 
		 * <p>
		 * E.g. This can be used to change the direction from diagonal to straight 
		 * (horizontal/vertical) when releasing an arrow key while another arrow key 
		 * is pressed.
		 * @param xAxis 
		 */
		public void cancelDirection(final boolean xAxis) {
			if (xAxis)
				this.dx = 0;
			else
				this.dy = 0;
		}
		
		/**
		 * Stop moving, by smoothly decelerating.
		 */
		public void decelerate() {
			constantVelocity = false;
		}
		
		/**
		 * Stop moving immediately.
		 */
		public void stopMoving() {
			if (timer != null && timer.getStatus() == Status.RUNNING) {
				timestamp = -1;
				timer.stop();
				if (viewer != null)
					viewer.setDoFasterRepaint(false);
			}
		}

	}
	
	
}
