package qupath.lib.gui.align;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

/**
 * Command to interactively adjust apply an affine transform to an image overlay.
 * 
 * @author Pete Bankhead
 *
 */
public class InteractiveImageAlignmentCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public InteractiveImageAlignmentCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (qupath.getImageData() == null) {
			DisplayHelpers.showNoImageError("Interactive image alignment");
			return;
		}
		new ImageAlignmentPane(qupath);
	}

}
