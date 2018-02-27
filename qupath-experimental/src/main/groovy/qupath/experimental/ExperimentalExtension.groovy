package qupath.experimental

import qupath.lib.classifiers.PixelClassifierGUI
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.extensions.QuPathExtension

/**
 * Extension to make more experimental commands present in the GUI.
 */
class ExperimentalExtension implements QuPathExtension {
    @Override
    void installExtension(QuPathGUI qupath) {
        QuPathGUI.addMenuItems(
                qupath.getMenu("Classify", true),
                qupath.createCommandAction(new PixelClassifierGUI(), "Pixel classifier (experimental)")
        )
    }

    @Override
    String getName() {
        return "Experimental commands"
    }

    @Override
    String getDescription() {
        return "New features that are still being developed or tested"
    }
}
