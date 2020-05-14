package qupath.process.gui.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.process.gui.ml.PixelClassifierPane;

/**
 * Open GUI for the current viewer to train a new pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierCommand implements Runnable {

	@Override
	public void run() {
		var qupath = QuPathGUI.getInstance();
		var imageData = qupath.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError("Pixel classifier");
		} else
			new PixelClassifierPane(qupath);
	}

}
