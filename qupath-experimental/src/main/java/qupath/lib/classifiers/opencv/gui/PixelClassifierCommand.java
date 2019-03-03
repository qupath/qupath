package qupath.lib.classifiers.opencv.gui;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

/**
 * Open GUI for the current viewer to train a new pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierCommand implements PathCommand {

	@Override
	public void run() {
		var viewer = QuPathGUI.getInstance().getViewer();
		var imageData = viewer == null ? null : viewer.getImageData();
		if (imageData == null) {
			DisplayHelpers.showErrorMessage("Pixel classifier", "No image open in current viewer!");
		} else
			new PixelClassifierImageSelectionPane(viewer);
	}

}
