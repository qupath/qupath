package qupath.lib.extension.svg;

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
        return "SVG export commands";
    }

    @Override
    public String getDescription() {
        return "New features that are still being developed or tested";
    }
}
