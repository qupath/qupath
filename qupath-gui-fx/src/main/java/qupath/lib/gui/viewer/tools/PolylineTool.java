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

import javafx.scene.input.MouseEvent;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * PathTool for drawing polygons.
 * 
 * @author Pete Bankhead
 *
 */
public class PolylineTool extends AbstractPolyROITool {
		
	public PolylineTool(ModeWrapper modes) {
		super(modes);
	}
	
	/**
	 * Returns false (no pixel snapping for the line tool).
	 */
	@Override
	protected boolean requestPixelSnapping() {
		return false;
	}
	
	@Override
	protected ROI createNewROI(MouseEvent e, double x, double y, ImagePlane plane) {
		return ROIs.createPolylineROI(x, y, plane);
	}
	
}
