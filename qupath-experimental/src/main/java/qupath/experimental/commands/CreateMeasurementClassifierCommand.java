package qupath.experimental.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;

/**
 * Command to create an object classifier based on thresholding a single measurement.
 * 
 * @author Pete Bankhead
 */
public class CreateMeasurementClassifierCommand implements PathCommand {
	
	private static String title = "Create measurement classifier";
	
	private QuPathGUI qupath;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public CreateMeasurementClassifierCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		Dialogs.showErrorNotification(title, "Not implemented yet!");
	}

}
