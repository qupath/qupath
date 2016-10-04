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

import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * PathTool for drawing lines.
 * 
 * @author Pete Bankhead
 *
 */
public class LineTool extends AbstractPathDraggingROITool {

	public LineTool(ModeWrapper modes) {
		super(modes);
	}
	
	
	@Override
	protected ROI createNewROI(double x, double y, int z, int t) {
		return new LineROI(x, y, x, y, -1, z, t);
	}

}
