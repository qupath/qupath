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
import java.util.Collections;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.QuPathGUI.Modes;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;

/**
 * Abstract PathTool for drawing ROIs that require clicking and dragging to draw.
 * 
 * @author Pete Bankhead
 *
 */
public abstract class AbstractPathDraggingROITool extends AbstractPathROITool {


	AbstractPathDraggingROITool(ModeWrapper modes) {
		super(modes);
	}


	@Override
	public void mouseDragged(MouseEvent e) {
		super.mouseDragged(e);
		
		if (!e.isPrimaryButtonDown()) {
            return;
        }
		
		ROI currentROI = viewer.getCurrentROI() instanceof ROI ? (ROI)viewer.getCurrentROI() : null;
		RoiEditor editor = viewer.getROIEditor();
		
		if (currentROI != null && editor.getROI() == currentROI && editor.hasActiveHandle()) {
			PathObject pathObject = viewer.getSelectedObject();
			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
			ROI roiUpdated = editor.setActiveHandlePosition(p.getX(), p.getY(), 0.25, e.isShiftDown());
			if (roiUpdated != currentROI) {
				((PathROIObject)pathObject).setROI(roiUpdated);
				viewer.repaint();
			}
			
			viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(pathObject), true);
//			editor.setActiveHandlePosition(x, y, minDisplacement, shiftDown)
//			currentROI.updateAdjustment(p.getX(), p.getY(), e.isShiftDown());
			viewer.repaint();
		}
	}

	
	@Override
	public void mouseReleased(MouseEvent e) {
		if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
		
		PathObject selectedObject = viewer.getSelectedObject();
		if (selectedObject == null)
			return;
		
		RoiEditor editor = viewer.getROIEditor();
		
		ROI currentROI = selectedObject.getROI();
		if (currentROI != null && editor.getROI() == currentROI && editor.hasActiveHandle()) {
//			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
			editor.setROI(null);
//			currentROI.finishAdjusting(p.getX(), p.getY(), e.isShiftDown());
			// Remove empty ROIs
			if (currentROI.isEmpty()) {
				if (selectedObject.getParent() != null)
					viewer.getHierarchy().removeObject(selectedObject, true);
				viewer.setSelectedObject(null);
			} else {
				commitObjectToHierarchy(e, selectedObject);
			}
			editor.ensureHandlesUpdated();
			editor.resetActiveHandle();
			if (PathPrefs.getReturnToMoveMode())
				modes.setMode(Modes.MOVE);
		}
	}
	
	
	
	private void commitObjectToHierarchy(MouseEvent e, PathObject pathObject) {
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
//		// Nothing to do for an empty object
//		if (pathObject.getROI() == null || pathObject.getROI().isEmpty()) {
//			if (pathObject.getParent() != null)
//				hierarchy.removeObject(pathObject, true);
//			return;
//		}
		hierarchy.addPathObject(viewer.getSelectedObject(), true); // Ensure object is within the hierarchy
		viewer.setSelectedObject(pathObject);
//		if (e.isAltDown()) {
//			if (e.isShiftDown())
//				PathObjectToolsAwt.combineAnnotations(viewer.getHierarchy(), pathObject, PathROIToolsAwt.CombineOp.ADD);
//			else
//				PathObjectToolsAwt.combineAnnotations(viewer.getHierarchy(), pathObject, PathROIToolsAwt.CombineOp.SUBTRACT);
//		}
	}
	

}
