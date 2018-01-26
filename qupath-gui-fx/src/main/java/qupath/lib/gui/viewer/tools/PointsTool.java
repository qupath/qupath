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
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import qupath.lib.geom.Point2;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;

/**
 * PathTool for adding points to point objects.
 * 
 * @author Pete Bankhead
 *
 */
public class PointsTool extends AbstractPathTool {

	
	public PointsTool(ModeWrapper modes) {
		super(modes);
	}


	private PointsROI getCurrentPoints() {
		PathObject currentObject = viewer.getSelectedObject();
		if (currentObject == null)
			return null;
		return (currentObject.getROI() instanceof PointsROI) ? (PointsROI)currentObject.getROI() : null;
	}
	
	
	@Override
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
		if (!e.isPrimaryButtonDown() || e.isConsumed()) {
            return;
        }
		
		PointsROI points = getCurrentPoints();
		if (points == null)
			return;

		RoiEditor editor = viewer.getROIEditor();
		editor.resetActiveHandle();
		
		viewer.getHierarchy().fireHierarchyChangedEvent(this, viewer.getSelectedObject());

//		// Find out the coordinates in the image domain & update the adjustment
//		Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
//		points.finishAdjusting(p.getX(), p.getY(), e.isShiftDown());
//		points.resetMeasurements();
	}
	
	
	@Override
	public void mouseDragged(MouseEvent e) {
		super.mouseDragged(e);
		if (!e.isPrimaryButtonDown() || e.isConsumed()) {
            return;
        }
		
		RoiEditor editor = viewer.getROIEditor();
		if (!(editor.getROI() instanceof PointsROI) || !editor.hasActiveHandle())
			return;
		
		PointsROI points = getCurrentPoints();
		
		// Find out the coordinates in the image domain & update the adjustment
		Point2D pAdjusting = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
//		double radius = PointsROI.getDefaultPointRadius();
		PointsROI points2 = (PointsROI)editor.setActiveHandlePosition(pAdjusting.getX(), pAdjusting.getY(), 0.25, e.isShiftDown());
		if (points2 == points)
			return;
		
		PathROIObject currentObject = (PathROIObject)viewer.getSelectedObject();
		currentObject.setROI(points2);
		viewer.repaint();
		
//		viewer.getHierarchy().fireHierarchyChangedEvent(this, currentObject);

//		//		points.updateAdjustment(pAdjusting.getX(), pAdjusting.getY(), e.isShiftDown());
//		
////		Point2 p = points.getNearest(pAdjusting.getX(), pAdjusting.getY(), radius);
//		if (p == null) {
//		} else {
//			p.setLocation(pAdjusting.getX(), pAdjusting.getY());
////			points.resetMeasurements();
//			viewer.repaint();
//		}
	}
	
	
	private PointsROI removeNearbyPoint(PointsROI points, double x, double y, double distance) {
		if (points == null)
			return points;
		Point2 p = points.getNearest(x, y, distance);
		return removePoint(points, p);
	}
	
	
	/**
	 * Alt-clicks remove the selected point, or selects a new 'family' of points (i.e. a different object) if
	 * a point from the current object isn't clicked.
	 * 
	 * @param x
	 * @param y
	 * @param currentObject
	 * @return
	 */
	private boolean handleAltClick(double x, double y, PathObject currentObject) {
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		double downsample = viewer.getDownsampleFactor();
		double distance = PathPrefs.getDefaultPointRadius();
		// Remove a point if the current selection has one
		if (currentObject != null && currentObject.isPoint()) {
			PointsROI points = (PointsROI)currentObject.getROI();
			PointsROI points2 = removeNearbyPoint(points, x, y, distance);
			if (points != points2) {
				((PathROIObject)currentObject).setROI(points2);
				hierarchy.fireHierarchyChangedEvent(this, currentObject);
				return true;
			}
		}
		
		// Activate a points object if there is one
		for (PathObject pathObject : hierarchy.getPointObjects(PathObject.class)) {
			// Don't check the current object again
			if (pathObject == currentObject)
				continue;
			// See if we've almost clicked on a point
			if (((PointsROI)pathObject.getROI()).getNearest(x, y, distance) != null) {
				viewer.setSelectedObject(pathObject);
//				hierarchy.getSelectionModel().setSelectedPathObject(pathObject);
				return true;
			}
		}
		// Select nothing
		viewer.setSelectedObject(null);
		
		return true;
	}
	
	
	@Override
	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		if (!e.isPrimaryButtonDown() || e.isConsumed()) {
            return;
        }
		
		// Get a server, if we can
		ImageServer<?> server = viewer.getServer();
		if (server == null)
			return;

		// Find out the coordinates in the image domain
		Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
		double xx = p.getX();
		double yy = p.getY();

		// If we are outside the image, ignore click
		if (xx < 0 || yy < 0 || xx >= server.getWidth() || yy >= server.getHeight())
			return;

		// See if we have a selected ROI
		PathObject currentObjectTemp = viewer.getSelectedObject();
		if (!(currentObjectTemp == null || currentObjectTemp instanceof PathROIObject))
			return;
		PathROIObject currentObject = (PathROIObject)currentObjectTemp;
		ROI currentROI = currentObject == null ? null : currentObject.getROI();
		
		RoiEditor editor = viewer.getROIEditor();
		double radius = PathPrefs.getDefaultPointRadius();
		
		PointsROI points = (currentROI instanceof PointsROI) ? (PointsROI)currentROI : null;
		// If Alt is pressed, try to delete a point
		if (e.isAltDown()) {
			handleAltClick(xx, yy, currentObject);
		}
		// Create a new ROI if we've got Alt & Shift pressed - or we just don't have a point ROI
		else if (points == null || (e.isShiftDown() && e.getClickCount() > 1)) {
			// PathPoints is effectively ready from the start - don't need to finalize
			points = new PointsROI(xx, yy);
			viewer.createAnnotationObject(points);
			editor.setROI(points);
			editor.grabHandle(xx, yy, radius, e.isShiftDown());
		} else if (points != null) {
			// Add point to current ROI, or adjust the position of a nearby point
			PointsROI points2 = addPoint(points, xx, yy, radius);
			if (points2 == points) {
				// If we didn't add a point, try to grab a handle
				if (!editor.grabHandle(xx, yy, radius, e.isShiftDown()))
					return;
				points2 = (PointsROI)editor.setActiveHandlePosition(xx, yy, 0.25, e.isShiftDown());
			} else {
				editor.setROI(points2);
				editor.grabHandle(xx, yy, radius, e.isShiftDown());
			}
			if (points2 != points) {
				currentObject.setROI(points2);
				viewer.getHierarchy().fireHierarchyChangedEvent(this, currentObject);
			}
		}
		viewer.repaint();
	}
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		ensureCursorType(Cursor.CROSSHAIR);
	}
	
	
	
	
	/**
	 * Add a point only if it has a separation of at least minimumSeparation from all other points
	 * currently stored, otherwise do nothing.
	 * 
	 * @param x
	 * @param y
	 * @param minimumSeparation
	 */
	private PointsROI addPoint(final PointsROI points, final double x, final double y, final double minimumSeparation) {
		// Can't add NaN points
		if (Double.isNaN(x + y))
			return points;

		List<Point2> pointsList = points.getPointList();
		if (minimumSeparation > 0) {
			// Test for separation
			double threshold = minimumSeparation*minimumSeparation;
			for (Point2 p : pointsList)
				if (p.distanceSq(x, y) < threshold)
					return points;
		}
		List<Point2> pointsList2 = new ArrayList<>(pointsList);
		pointsList2.add(new Point2(x, y));
		return new PointsROI(pointsList2, points.getC(), points.getZ(), points.getT());
	}
	
	
	
	private PointsROI removePoint(final PointsROI points, final Point2 point) {
		if (point == null)
			return points;
		List<Point2> pointsList = new ArrayList<>(points.getPointList());
		if (pointsList.remove(point)) {
			return new PointsROI(pointsList, points.getC(), points.getZ(), points.getT());
		}
		return points;
	}
	
	

}
