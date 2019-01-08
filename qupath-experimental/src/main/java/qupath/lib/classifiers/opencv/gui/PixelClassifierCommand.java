package qupath.lib.classifiers.opencv.gui;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;

public class PixelClassifierCommand implements PathCommand {

	@Override
	public void run() {
		new PixelClassifierImageSelectionPane(QuPathGUI.getInstance().getViewer());
	}

}
