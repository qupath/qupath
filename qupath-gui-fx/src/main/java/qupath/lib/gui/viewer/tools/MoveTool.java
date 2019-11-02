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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.Animation.Status;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
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
public class MoveTool extends AbstractPathTool {
	
	final static private Logger logger = LoggerFactory.getLogger(MoveTool.class);

	private Point2D pDragging;
	private double dx, dy; // Last dragging displacements
	private long lastDragTimestamp; // Used to determine if the user has stopped dragging (but may not yet have release the mouse button)
	
	private ViewerMover mover;
	
	
	public MoveTool(final ModeWrapper modes) {
		super(modes);
	}

	
	@Override
	public void registerTool(final QuPathViewer viewer) {
		super.registerTool(viewer);
		mover = new ViewerMover(viewer);
	}
	
	
	@Override
	public void mousePressed(MouseEvent e) {
		
		mover.stopMoving();
		
		super.mousePressed(e);
		
		if (!e.isPrimaryButtonDown() || e.isConsumed())
            return;
		
		boolean snapping = false;
		Point2D p = mouseLocationToImage(e, false, snapping);
		double xx = p.getX();
		double yy = p.getY();
		
		// If we are double-clicking, see if we can access a ROI
		// TODO: Consider whether Alt & the Shortcut key should both have the same effect
		if (e.getClickCount() > 1 || e.isAltDown() || e.isShortcutDown()) {
			boolean selected = false;
			if (e.isAltDown() || e.isShortcutDown())
				selected = tryToSelect(xx, yy, e.getClickCount()-1, true, true);
			else
				selected = tryToSelect(xx, yy, e.getClickCount()-2, false);
			e.consume();
			pDragging = null;
			if (!selected && PathPrefs.getDoubleClickToZoom()) {
				double downsample = viewer.getDownsampleFactor();
				if (e.isAltDown() || e.isShortcutDown())
					downsample *= 2;
				else
					downsample /= 2;
				viewer.setDownsampleFactor(downsample, e.getX(), e.getY());
			}
			return;
		}

//		// If we are double-clicking, see if we can access a ROI
//		if (e.getClickCount() > 1 || e.isAltDown()) {
//			if (e.isAltDown())
//				tryToSelect(xx, yy, e.getClickCount()-1, true, true);
//			else
//				tryToSelect(xx, yy, e.getClickCount()-2, false);
//			e.consume();
//			pDragging = null;
//			return;
//		}
		
		if (!viewer.isSpaceDown() && viewer.getHierarchy() != null) {
			
			// Set the current parent object based on the first click
			PathObject currentObject = viewer.getSelectedObject();
			PathObject parent = currentObject == null ? null : currentObject.getParent();
			if (parent != null && parent.isDetection())
				parent = null;
			setCurrentParent(viewer.getHierarchy(), parent, currentObject);
			
			// See if we can get a handle to edit the ROI
			// Don't want to edit detections / TMA cores
			ROI currentROI = viewer.getCurrentROI();
			RoiEditor editor = viewer.getROIEditor();
			// Try dealing with having a ROI first
			if (currentROI != null) {
				if (editor.getROI() == currentROI) {
					// 1.5 increases the range; the handle radius alone is too small a distance, especially if the handles are painted as squares -
					// because 1.5 >~ sqrt(2) it ensures that at least the entire square is 'active' (and a bit beyond it)
					double search = viewer.getMaxROIHandleSize() * 1.5;
//					if (snapping && search < 1)
//						search = 1;
					if (editor.grabHandle(xx, yy, search, e.isShiftDown()))
						e.consume();
				}
				if (!e.isConsumed() && canAdjust(currentObject) &&
						(RoiTools.areaContains(currentROI, xx, yy) || getSelectableObjectList(xx, yy).contains(currentObject))) {
					// If we have a translatable ROI, try starting translation
					if (editor.startTranslation(xx, yy))
						e.consume();
				}
				if (e.isConsumed()) {
					pDragging = null;
					return;
				}
			}
		}
		
		// Store point for drag-to-pan
        pDragging = mouseLocationToImage(e, false, true);
//        viewer.setDoFasterRepaint(true); // Turn on if dragging is too slow
	}
	
	public static boolean canAdjust(PathObject pathObject) {
		return (pathObject != null && pathObject.isEditable());
//		return (pathObject != null && !(pathObject instanceof PathDetectionObject) && pathObject.hasROI() && !GeneralHelpers.containsClass(pathObject.getPathObjectList(), PathDetectionObject.class));
	}
	
	
	@Override
	public void mouseDragged(MouseEvent e) {
		
		mover.stopMoving();
		
		super.mouseDragged(e);
		
		if (!e.isPrimaryButtonDown() || e.isConsumed())
            return;

		// Handle ROIs if the spacebar isn't down
		if (!viewer.isSpaceDown()) {
			
			RoiEditor editor = viewer.getROIEditor();
			Point2D p = mouseLocationToImage(e, true, requestPixelSnapping() &&
					editor.hasROI() && editor.getROI().isArea());

			// Try moving handle
			if (editor != null && editor.hasActiveHandle()) {
				ROI updatedROI = editor.setActiveHandlePosition(p.getX(), p.getY(), viewer.getDownsampleFactor()/2.0, e.isShiftDown());
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
	//				System.err.println("Changing... " + viewer.getHierarchy().nObjects());
					
//					viewer.repaintImageRegion(boundsIntersection, false);
				}
				pDragging = null;
				return;
			}
			
			// Try to select objects, if alt is down
			if (e.isAltDown()) {
				tryToSelect(p.getX(), p.getY(), e.getClickCount()-1, true, false);
				e.consume();
				return;
			}
		}
		
		// Don't allow dragging if 'zoom to fit' selected
		if (viewer.getZoomToFit())
			return;
		
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
//						PathObject parentPrevious = pathObject.getParent();
						hierarchy.removeObjectWithoutUpdate(pathObject, true);
						if (getCurrentParent() == null || !PathPrefs.getClipROIsForHierarchy() || e.isShiftDown())
							hierarchy.addPathObject(pathObject);
						else
							hierarchy.addPathObjectBelowParent(getCurrentParent(), pathObject, true);
//						PathObject parentNew = pathObject.getParent();
//						if (parentPrevious == parentNew)
//							hierarchy.fireHierarchyChangedEvent(this, parentPrevious);
//						else
//							hierarchy.fireHierarchyChangedEvent(this);
					}
				}
				viewer.setSelectedObject(pathObject);				
			}
		}
		
		
		// Optionally continue a dragging movement until the canvas comes to a standstill
		if (pDragging != null && PathPrefs.requestDynamicDragging() && System.currentTimeMillis() - lastDragTimestamp < 100 && (dx*dx + dy*dy > viewer.getDownsampleFactor())) {
			mover.startMoving(dx, dy, false);
		} else
	        viewer.setDoFasterRepaint(false);
		
		
		// Make sure we don't have a previous point (to prevent weird dragging artefacts)
		pDragging = null;
		
//		// If we were translating, stop
//		if (editor.isTranslating()) {
//			editor.finishTranslation();
//			// TODO: Make this more efficient!
//			viewer.getPathObjectHierarchy().fireHierarchyChangedEvent();
//			return;
//		}
	}
	
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		
		// We don't want to change a waiting cursor unnecessarily
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
			Point2D p2 = mouseLocationToImage(e, true, requestPixelSnapping());
			double xx = p2.getX();
			double yy = p2.getY();
			if (RoiTools.areaContains(currentROI, xx, yy)) {
				ensureCursorType(Cursor.MOVE);
				return;
			}
		}
		ensureCursorType(Cursor.HAND);
	}

	
	
	
	
	
	
	public static class ViewerMover {
		
		private QuPathViewer viewer;
		private Timeline timer; // Timeline used to continue dragging movements
		private long timestamp = -1;
		
		private int heartbeat = 10; // Requested heartbeat in ms... 10 is infeasibly fast?
		
		private double dx, dy; // Last dragging displacements
		private boolean constantVelocity = false;
		
		
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
			
//			System.out.println("Timestamp: " + timestamp + ", New timestamp: " + newTimestamp + ", dx: " + dx + ", dy: " + dy + ", scale: " + scale);
			timestamp = newTimestamp;
			dx *= scale;
			dy *= scale;
			double downsample = viewer.getDownsampleFactor();
			if (dx*dx + dy*dy < downsample*downsample*4) {
				stopMoving();
				return;
			}
			
//			System.err.println("Call by distance: " + (Math.sqrt(dx*dx + dy*dy) / Math.sqrt(downsample*downsample)));
			viewer.setCenterPixelLocation(viewer.getCenterPixelX() - dx, viewer.getCenterPixelY() - dy);
		}
		
		/**
		 * Start moving, with initial velocity given by dx and dy.
		 * @param dx
		 * @param dy
		 */
		public void startMoving(final double dx, final double dy, final boolean constantVelocity) {
			this.dx = dx;
			this.dy = dy;
			this.constantVelocity = constantVelocity;
//			System.out.println("Starting: " + dx + ", " + dy);
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
				timer.setCycleCount(Timeline.INDEFINITE);
			}
//			timer.setCoalesce(true);
			timestamp = System.currentTimeMillis();
			timer.playFromStart();
		}
		
		
		public void decelerate() {
			constantVelocity = false;
		}
		

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
