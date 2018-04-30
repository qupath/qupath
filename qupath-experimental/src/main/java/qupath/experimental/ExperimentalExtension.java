package qupath.experimental;

import qupath.lib.classifiers.gui.PixelClassifierGUI;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.align.InteractiveImageAlignmentCommand;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class ExperimentalExtension implements QuPathExtension {
	
    @Override
    public void installExtension(QuPathGUI qupath) {
        QuPathGUI.addMenuItems(
                qupath.getMenu("Classify", true),
                QuPathGUI.createCommandAction(PixelClassifierGUI.getInstance(), "Pixel classifier (experimental)")
        );
        QuPathGUI.addMenuItems(
                qupath.getMenu("Analyze", true),
                QuPathGUI.createCommandAction(new InteractiveImageAlignmentCommand(qupath), "Interactive image alignment (experimental)")
        );
    }

    @Override
    public String getName() {
        return "Experimental commands";
    }

    @Override
    public String getDescription() {
        return "New features that are still being developed or tested";
    }
}
