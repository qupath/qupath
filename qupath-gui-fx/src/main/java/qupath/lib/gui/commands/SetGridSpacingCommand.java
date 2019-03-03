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

package qupath.lib.gui.commands;

import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.viewer.GridLines;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Command to set the spacing of the (counting) grid.
 * 
 * @deprecated in favour of using {@link qupath.lib.gui.prefs.PathPrefs} and the preference pane.
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
public class SetGridSpacingCommand implements PathCommand {
	
	private OverlayOptions overlayOptions;
	
	public SetGridSpacingCommand(final OverlayOptions overlayOptions) {
		super();
		this.overlayOptions = overlayOptions;
	}

	@Override
	public void run() {
		GridLines gridLines = overlayOptions.getGridLines();
		
		ParameterList params = new ParameterList()
				.addDoubleParameter("hSpacing", "Horizontal spacing", gridLines.getSpaceX())
				.addDoubleParameter("vSpacing", "Vertical spacing", gridLines.getSpaceX())
				.addBooleanParameter("useMicrons", "Use microns", gridLines.useMicrons());
		
		if (!DisplayHelpers.showParameterDialog("Set grid spacing", params))
			return;
		
		gridLines = new GridLines();
		gridLines.setSpaceX(params.getDoubleParameterValue("hSpacing"));
		gridLines.setSpaceY(params.getDoubleParameterValue("vSpacing"));
		gridLines.setUseMicrons(params.getBooleanParameterValue("useMicrons"));
		
		overlayOptions.gridLinesProperty().set(gridLines);
	}

}
