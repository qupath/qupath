package qupath.lib.gui.align;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

public class InteractiveImageAlignmentCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	public InteractiveImageAlignmentCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (qupath.getImageData() == null) {
			DisplayHelpers.showErrorMessage("Interactive image alignment", "Please open a 'base' image first!");
			return;
		}
		new ImageAlignmentPane(qupath);
	}

}
