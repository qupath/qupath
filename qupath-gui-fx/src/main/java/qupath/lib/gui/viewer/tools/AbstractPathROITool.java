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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.QuPathGUI.Modes;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.RoiEditor;
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
	 * @return
	 */
	protected abstract ROI createNewROI(double x, double y, int z, int t);
	
	
	@Override
	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		if (!e.isPrimaryButtonDown() || e.isConsumed()) {
            return;
        }
		
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return;
		
		// Find out the coordinates in the image domain
		Point2D p2 = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
		double xx = p2.getX();
		double yy = p2.getY();
		
		PathObject currentObject = viewer.getSelectedObject();
		ROI currentROI = viewer.getCurrentROI();
		
		RoiEditor editor = viewer.getROIEditor();

		boolean adjustingPolygon = (currentROI instanceof PolygonROI || currentROI instanceof PolylineROI) && editor.getROI() == currentROI && (editor.isTranslating() || editor.hasActiveHandle());

		// If we are double-clicking & we don't have a polygon, see if we can access a ROI
		if (!adjustingPolygon && e.getClickCount() > 1) {
			// Reset parent... for now
			resetCurrentParent();		
			tryToSelect(xx, yy, e.getClickCount()-2, false);
			e.consume();
			return;
		}

//		PathObjectSelectionModel selectionModel = hierarchy.getSelectionModel();
		// If we double-clicked a polygon, we're done with it
		if (adjustingPolygon) {
			if (e.getClickCount() == 1) {
				logger.trace("Adjusting polygon {}", e);
				ROI roiUpdated = editor.requestNewHandle(p2.getX(), p2.getY());
				if (currentObject != null && currentObject.getROI() != roiUpdated && currentObject instanceof PathROIObject) {
					((PathROIObject)currentObject).setROI(roiUpdated);
//					viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(currentObject));
				}
				viewer.repaint();
			}
			else {
				completePolygon(e);
			}
			return;
		}
		
		// Set the current parent object based on the first click
		setCurrentParent(hierarchy, getSelectableObject(xx, yy, 0), null);
		
		PathObject pathObject = PathObjects.createAnnotationObject(
				createNewROI(xx, yy, viewer.getZPosition(), viewer.getTPosition()),
				PathPrefs.getAutoSetAnnotationClass()
				);
		viewer.setSelectedObject(pathObject);
		// Start editing the ROI immediately
		editor.setROI(pathObject.getROI());
		editor.grabHandle(xx, yy, viewer.getMaxROIHandleSize() * 1.5, e.isShiftDown());
	}
	
	
	/**
	 * Finish adjusting a polygon or polyline.
	 * 
	 * @param e
	 * @param hierarchy
	 * @param editor
	 * @param currentObject
	 * @param currentROI
	 */
	void completePolygon(MouseEvent e) {
		logger.trace("Completing polygon  {}", e);
		
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		PathObject currentObject = viewer.getSelectedObject();
		ROI currentROI = viewer.getCurrentROI();
		RoiEditor editor = viewer.getROIEditor();
		
		if (requestParentClipping(e)) {
			currentROI = refineROIByParent(currentROI);
			((PathAnnotationObject)currentObject).setROI(currentROI);
		}
		
		// If the polygon is just a single point, get rid of it
		if (currentROI.isEmpty()) {
			currentROI = null;
			viewer.setSelectedObject(null);
			if (currentObject.getParent() != null)
				hierarchy.removeObject(currentObject, true);
			editor.setROI(null);
		} else {
			if (e.isShiftDown() && getCurrentParent() != null)
				hierarchy.addPathObjectBelowParent(getCurrentParent(), currentObject, false, true);
			else
				hierarchy.addPathObject(currentObject, true);						
			viewer.setSelectedObject(currentObject);
			editor.resetActiveHandle();
		}
		if (PathPrefs.getReturnToMoveMode())
			modes.setMode(Modes.MOVE);
	}
	
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		ensureCursorType(Cursor.CROSSHAIR);
	}

}
