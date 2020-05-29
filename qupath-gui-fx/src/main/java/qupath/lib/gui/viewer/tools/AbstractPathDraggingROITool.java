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
import java.util.Collections;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;

/**
 * Abstract {@link PathTool} for drawing ROIs that require clicking and dragging to create 
 * two end points (either for a bounding box or end points of a line).
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractPathDraggingROITool extends AbstractPathROITool {

	@Override
	public void mouseDragged(MouseEvent e) {
		super.mouseDragged(e);
		
		if (!e.isPrimaryButtonDown()) {
            return;
        }
		
		var viewer = getViewer();
		ROI currentROI = viewer.getCurrentROI() instanceof ROI ? (ROI)viewer.getCurrentROI() : null;
		RoiEditor editor = viewer.getROIEditor();
		
		if (currentROI != null && editor.getROI() == currentROI && editor.hasActiveHandle()) {
			PathObject pathObject = viewer.getSelectedObject();
			Point2D p = mouseLocationToImage(e, true, requestPixelSnapping());
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
		
		var viewer = getViewer();
		PathObject selectedObject = viewer.getSelectedObject();
		if (selectedObject == null)
			return;
		
		RoiEditor editor = viewer.getROIEditor();
		
		ROI currentROI = selectedObject.getROI();
		if (currentROI != null && editor.getROI() == currentROI && editor.hasActiveHandle()) {
			editor.setROI(null);
			// Remove empty ROIs
			if (currentROI.isEmpty()) {
				if (selectedObject.getParent() != null)
					viewer.getHierarchy().removeObject(selectedObject, true);
				viewer.setSelectedObject(null);
			} else {
				commitObjectToHierarchy(e, selectedObject);
			}
//			editor.ensureHandlesUpdated();
//			editor.resetActiveHandle();
//			if (PathPrefs.getReturnToMoveMode())
//				modes.setMode(Modes.MOVE);
		}
	}
	

}
