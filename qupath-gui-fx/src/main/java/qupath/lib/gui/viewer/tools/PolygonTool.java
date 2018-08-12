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

import javafx.scene.input.MouseEvent;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;

/**
 * PathTool for drawing polygons.
 * 
 * @author Pete Bankhead
 *
 */
public class PolygonTool extends AbstractPathROITool {
	
	public PolygonTool(ModeWrapper modes) {
		super(modes);
	}
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		
		ROI currentROI = viewer.getCurrentROI();
		RoiEditor editor = viewer.getROIEditor();
		
		if ((currentROI instanceof PolygonROI) && editor.getROI() == currentROI) {
			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
			ROI roiUpdated = editor.setActiveHandlePosition(p.getX(), p.getY(), viewer.getDownsampleFactor(), e.isShiftDown());
//			ROI roiUpdated = editor.setActiveHandlePosition(p.getX(), p.getY(), viewer.getROIHandleSize() * 1.5, e.isShiftDown());
			PathObject pathObject = viewer.getSelectedObject();
			if (roiUpdated != currentROI && pathObject instanceof PathROIObject) {
				((PathROIObject)pathObject).setROI(roiUpdated);
				viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(pathObject), true);
//				viewer.repaint();
//				System.err.println("N vertices: " + ((PolygonROI)roiUpdated).nVertices());
			}
			
//			((PolygonROI)currentROI).setTempDrawingLocation(p.getX(), p.getY());
//			viewer.repaint(); // TODO: Consider clip mask for this
		}
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		// Note: if the 'freehand' part of the polygon creation isn't desired, just comment out this whole method
		super.mouseDragged(e);
		if (!e.isPrimaryButtonDown()) {
            return;
        }

		ROI currentROI = viewer.getCurrentROI();
		RoiEditor editor = viewer.getROIEditor();
		
		if ((currentROI instanceof PolygonROI) && editor.getROI() == currentROI) {
			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
			ROI roiUpdated = editor.requestNewHandle(p.getX(), p.getY());
			PathObject pathObject = viewer.getSelectedObject();
			if (roiUpdated != currentROI && pathObject instanceof PathROIObject) {
				((PathROIObject)pathObject).setROI(roiUpdated);
				viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(pathObject), true);
//				viewer.repaint();
			}
			
//			((PolygonROI)currentROI).setTempDrawingLocation(p.getX(), p.getY());
//			viewer.repaint(); // TODO: Consider clip mask for this
		}
	}
	

	@Override
	protected ROI createNewROI(double x, double y, int z, int t) {
		return ROIs.createPolyonROI(x, y, -1, z, t);
//		return new PolygonROI(new float[]{(float)x, (float)x}, new float[]{(float)y, (float)y}, -1, z, t);
	}
	
}
