package qupath.lib.gui.align;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

/**
 * Command to interactively adjust apply an affine transform to an image overlay.
 * 
 * @author Pete Bankhead
 *
 */
public class InteractiveImageAlignmentCommand implements Runnable {
	
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
			Dialogs.showNoImageError("Interactive image alignment");
			return;
		}
		new ImageAlignmentPane(qupath);
	}

}
