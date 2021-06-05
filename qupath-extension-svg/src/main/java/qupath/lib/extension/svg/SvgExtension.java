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

package qupath.lib.extension.svg;

import qupath.lib.common.GeneralTools;
import qupath.lib.extension.svg.SvgExportCommand.SvgExportType;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

/**
 * Extension for SVG image export.
 */
public class SvgExtension implements QuPathExtension {
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	
    	var actionExport = ActionTools.createAction(new SvgExportCommand(qupath, SvgExportType.SELECTED_REGION), "Rendered SVG");
    	actionExport.disabledProperty().bind(qupath.imageDataProperty().isNull());
    	actionExport.setLongText("Export the current selected region as a rendered (RGB) SVG image. "
    			+ "Any annotations and ROIs will be stored as vectors, which can later be adjusted in other software.");
    	var actionSnapshot = ActionTools.createAction(new SvgExportCommand(qupath, SvgExportType.VIEWER_SNAPSHOT), "Current viewer content (SVG)");
    	actionSnapshot.setLongText("Export an RGB snapshot of the current viewer content as an SVG image. "
    			+ "Any annotations and ROIs will be stored as vectors, which can later be adjusted in other software.");
    	
    	MenuTools.addMenuItems(
                qupath.getMenu("File>Export images...", true),
                actionExport
        );
    	MenuTools.addMenuItems(
                qupath.getMenu("File>Export snapshot...", true),
                actionSnapshot
        );
    	
    }

    @Override
    public String getName() {
        return "SVG export extension";
    }

    @Override
    public String getDescription() {
        return "Export snapshots and images in SVG format";
    }
    
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public String getQuPathVersion() {
		return GeneralTools.getPackageVersion(SvgExtension.class);
	}
	
}