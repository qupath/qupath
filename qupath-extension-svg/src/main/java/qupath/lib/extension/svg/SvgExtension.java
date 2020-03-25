package qupath.lib.extension.svg;

import qupath.lib.extension.svg.commands.SvgExportCommand;
import qupath.lib.extension.svg.commands.SvgExportCommand.SvgExportType;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class SvgExtension implements QuPathExtension {
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	
    	MenuTools.addMenuItems(
                qupath.getMenu("File>Export images...", true),
                QuPathGUI.createCommandAction(new SvgExportCommand(qupath, SvgExportCommand.SvgExportType.SELECTED_REGION),
                		"Rendered SVG")
        );
    	MenuTools.addMenuItems(
                qupath.getMenu("File>Export snapshot...", true),
                QuPathGUI.createCommandAction(new SvgExportCommand(qupath, SvgExportCommand.SvgExportType.VIEWER_SNAPSHOT),
                		"Current viewer content (SVG)")
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
