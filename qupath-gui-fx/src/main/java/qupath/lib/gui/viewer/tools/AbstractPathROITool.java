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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.QuPathGUI.Modes;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * Abstract PathTool for drawing ROIs.
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractPathROITool extends AbstractPathTool {
	
	final private static Logger logger = LoggerFactory.getLogger(AbstractPathROITool.class);
	
	
	AbstractPathROITool(ModeWrapper modes) {
		super(modes);
	}


	/**
	 * Create a new ROI with the given starting coordinates.
	 * 
	 * @param x
	 * @param y
	 * @param plane
	 * @return
	 */
	protected abstract ROI createNewROI(double x, double y, ImagePlane plane);
	
	/**
	 * Create a new annotation & set it in the current viewer.
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	PathObject createNewAnnotation(double x, double y) {
		
		var currentObject = viewer.getSelectedObject();
		var editor = viewer.getROIEditor();
		if (currentObject != null && currentObject.getParent() == null && currentObject.getROI() == editor.getROI() && (editor.isTranslating() || editor.hasActiveHandle())) {
			logger.warn("Creating a new annotation before a previous one was complete - {} will be discarded!", currentObject);
		}
		
		logger.trace("Creating new annotation at ({}, {}", x, y);
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null) {
			logger.warn("Cannot create new annotation - no hierarchy available!");
			return null;
		}
		ROI roi = createNewROI(x, y, viewer.getImagePlane());
		PathObject pathObject = PathObjects.createAnnotationObject(roi, PathPrefs.getAutoSetAnnotationClass());
		var selectionModel = hierarchy.getSelectionModel();
		if (PathPrefs.isSelectionMode() && !selectionModel.noSelection())
			selectionModel.setSelectedObject(pathObject, true);			
		else
			viewer.setSelectedObject(pathObject);
		return pathObject;
	}
	
	
	@Override
	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		if (!e.isPrimaryButtonDown() || e.isConsumed()) {
            return;
        }
		
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return;
		
		PathObject currentObject = viewer.getSelectedObject();
		ROI currentROI = currentObject == null ? null : currentObject.getROI();
		RoiEditor editor = viewer.getROIEditor();
		
		boolean adjustingPolygon = (currentROI instanceof PolygonROI || currentROI instanceof PolylineROI) && editor.getROI() == currentROI && (editor.isTranslating() || editor.hasActiveHandle());

		// If we're adjusting a polygon/polyline with an appropriate tool, return at leave it up to the tool to handle the custom things
		if (adjustingPolygon) {
			if (viewer.getMode() == Modes.POLYGON || viewer.getMode() == Modes.POLYLINE)
				return;
			else {
				viewer.getHierarchy().getSelectionModel().clearSelection();
				viewer.getHierarchy().fireHierarchyChangedEvent(currentObject);
			}
		}

		// Find out the coordinates in the image domain
		Point2D p2 = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
		double xx = p2.getX();
		double yy = p2.getY();
						
		// If we are double-clicking & we don't have a polygon, see if we can access a ROI
		if (e.getClickCount() > 1) {
			// Reset parent... for now
			resetCurrentParent();		
			tryToSelect(xx, yy, e.getClickCount()-2, false);
			e.consume();
			return;
		}

		// Set the current parent object based on the first click
		setCurrentParent(hierarchy, getSelectableObject(xx, yy, 0), null);
		
		// Create a new annotation
		PathObject pathObject = createNewAnnotation(xx, yy);
		
		// Start editing the ROI immediately
		editor.setROI(pathObject.getROI());
		editor.grabHandle(xx, yy, viewer.getMaxROIHandleSize() * 1.5, e.isShiftDown());
	}
	
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		ensureCursorType(Cursor.CROSSHAIR);
	}

	
	
	/**
	 * When drawing an object is complete, add it to the hierarchy - or whatever else is required.
	 * 
	 * @param e
	 * @param pathObject
	 */
	void commitObjectToHierarchy(MouseEvent e, PathObject pathObject) {
		if (pathObject == null)
			return;
		
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		
		var currentROI = pathObject.getROI();
		
		// If we are in selection mode, try to get objects to select
		if (PathPrefs.isSelectionMode()) {
			var pathClass = PathPrefs.getAutoSetAnnotationClass();
			var toSelect = new ArrayList<PathObject>();
			var reclassified = new ArrayList<PathObject>();
			if (currentROI instanceof PathArea) {
				var pathArea = (PathArea)currentROI;
				for (var object : viewer.getHierarchy().getObjectsForRegion(null, ImageRegion.createInstance(currentROI), null)) {
					if (object == pathObject)
						continue;
					var temp = object.getROI();
					if (pathArea.contains(temp.getCentroidX(), temp.getCentroidY())) {
						toSelect.add(object);
						if (pathClass != null && object.getPathClass() != pathClass) {
							object.setPathClass(pathClass);
							reclassified.add(object);
						}
					}
				}
				if (!reclassified.isEmpty())
					viewer.getHierarchy().fireObjectClassificationsChangedEvent(this, reclassified);
				if (pathObject.getParent() != null)
					viewer.getHierarchy().removeObject(pathObject, true);
				else
					viewer.getHierarchy().fireHierarchyChangedEvent(this);
				if (toSelect.isEmpty())
					viewer.setSelectedObject(null);
				else if (e.isShiftDown()) {
					viewer.getHierarchy().getSelectionModel().deselectObject(pathObject);
					viewer.getHierarchy().getSelectionModel().selectObjects(toSelect);
				} else
					viewer.getHierarchy().getSelectionModel().setSelectedObjects(toSelect, null);
			}
		} else {
			if (!requestParentClipping(e)) {
				if (currentROI.isEmpty()) {
					pathObject = null;
				} else
					hierarchy.addPathObject(pathObject, true); // Ensure object is within the hierarchy
			} else {
				ROI roiNew = refineROIByParent(pathObject.getROI());
				if (roiNew.isEmpty()) {
					hierarchy.removeObject(pathObject, true);
					pathObject = null;
				} else {
					((PathAnnotationObject)pathObject).setROI(roiNew);
					hierarchy.addPathObjectBelowParent(getCurrentParent(), pathObject, false, true);
				}
			}
			if (pathObject != null)
				viewer.setSelectedObject(pathObject);
			else
				viewer.getHierarchy().getSelectionModel().clearSelection();
		}
				
		var editor = viewer.getROIEditor();
		editor.ensureHandlesUpdated();
		editor.resetActiveHandle();
		if (PathPrefs.getReturnToMoveMode() && modes.getMode() != Modes.BRUSH && modes.getMode() != Modes.WAND)
			modes.setMode(Modes.MOVE);
	}
	
	
	@Override
	public void deregisterTool(QuPathViewer viewer) {
		super.deregisterTool(viewer);
	}
	
}
