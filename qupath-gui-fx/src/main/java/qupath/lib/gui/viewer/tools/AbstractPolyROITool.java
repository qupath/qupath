/*-
 * #%L
 * This file is part of QuPath.
 * %%
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
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.input.MouseEvent;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;

abstract class AbstractPolyROITool extends AbstractPathROITool {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractPolyROITool.class);

	boolean isFreehandPolyROI = false;

	
	@Override
	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		
		// If we double-clicked a polygon, we're done with it
		var viewer = getViewer();
		PathObject currentObject = viewer == null ? null : viewer.getSelectedObject();
		if (currentObject != null && e.getClickCount() == 1) {
			RoiEditor editor = viewer.getROIEditor();
			logger.trace("Adjusting polygon {}", e);
			Point2D p2 = mouseLocationToImage(e, true, requestPixelSnapping());
			ROI roiUpdated = editor.requestNewHandle(p2.getX(), p2.getY());
			if (currentObject != null && currentObject.getROI() != roiUpdated && currentObject instanceof PathROIObject) {
				((PathROIObject)currentObject).setROI(roiUpdated);
			}
			isFreehandPolyROI = false;
			viewer.repaint();
		} else {
			commitObjectToHierarchy(e, currentObject);
		}
		
		ROI currentROI = currentObject == null ? null : currentObject.getROI();
		if (isPolyROI(currentROI) && currentROI.isEmpty() && 
				(currentROI.getNumPoints() == 1 || new HashSet<>(currentROI.getAllPoints()).size() == 1))
			isFreehandPolyROI = true;
	}
	
	
	@Override
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
		
		if (isFreehandPolyROI) {
			var viewer = getViewer();
			PathObject currentObject = viewer.getSelectedObject();
			ROI currentROI = currentObject == null ? null : currentObject.getROI();
			
			if (isPolyROI(currentROI) && currentROI.isEmpty()) {
				isFreehandPolyROI = false;
			} else if (PathPrefs.enableFreehandToolsProperty().get()) {
				RoiEditor editor = viewer.getROIEditor();
				Point2D p2 = mouseLocationToImage(e, true, requestPixelSnapping());
				ROI roiUpdated = editor.setActiveHandlePosition(p2.getX(), p2.getY(), viewer.getDownsampleFactor(), e.isShiftDown());
				if (currentObject != null && currentObject.getROI() != roiUpdated && currentObject instanceof PathROIObject) {
					((PathROIObject)currentObject).setROI(roiUpdated);
				}
				commitObjectToHierarchy(e, currentObject);
//				completePolygon(e);
			}
		}
	}
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		
		var viewer = getViewer();
		ROI currentROI = viewer.getCurrentROI();
		RoiEditor editor = viewer.getROIEditor();
		
		if (isPolyROI(currentROI) && editor.getROI() == currentROI) {
			Point2D p = mouseLocationToImage(e, true, requestPixelSnapping());
			ROI roiUpdated = editor.setActiveHandlePosition(p.getX(), p.getY(), viewer.getDownsampleFactor(), e.isShiftDown());
			PathObject pathObject = viewer.getSelectedObject();
			if (roiUpdated != currentROI && pathObject instanceof PathROIObject) {
				((PathROIObject)pathObject).setROI(roiUpdated);
				viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(pathObject), true);
			}
		}
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		// Note: if the 'freehand' part of the polygon creation isn't desired, just comment out this whole method
		super.mouseDragged(e);
		if (!e.isPrimaryButtonDown()) {
            return;
        }

		var viewer = getViewer();
		ROI currentROI = viewer.getCurrentROI();
		RoiEditor editor = viewer.getROIEditor();
		
		if (isPolyROI(currentROI) && editor.getROI() == currentROI) {
			Point2D p = mouseLocationToImage(e, true, requestPixelSnapping());
			ROI roiUpdated = editor.requestNewHandle(p.getX(), p.getY());
			PathObject pathObject = viewer.getSelectedObject();
			if (roiUpdated != currentROI && pathObject instanceof PathROIObject) {
				((PathROIObject)pathObject).setROI(roiUpdated);
				viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(pathObject), true);
			}
		}
	}
	
	
	boolean isPolyROI(ROI roi) {
		return roi instanceof PolygonROI || roi instanceof PolylineROI;
	}

}