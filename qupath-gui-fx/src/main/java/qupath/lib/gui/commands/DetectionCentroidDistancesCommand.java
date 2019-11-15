package qupath.lib.gui.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.DistanceTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Calculate the distances between detection centroids for each available classification.
 * 
 * @author Pete Bankhead
 *
 * @see DistanceToAnnotationsCommand
 */
public class DetectionCentroidDistancesCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(DetectionCentroidDistancesCommand.class);
	
	private QuPathGUI qupath;
	
	public DetectionCentroidDistancesCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		var imageData = qupath.getImageData();
		if (imageData == null)
			return;
		
		if (imageData.getServer().nZSlices() > 1) {
			logger.debug("Warning user that measurements will be 2D...");
			if (!Dialogs.showConfirmDialog("Detection centroid distances 2D", 
					"Detection centroid distances command works only in 2D - distances will not be calculated for objects on different z-slices or time-points")) {
				logger.debug("Command cancelled");
				return;
			}
		}
		
		DistanceTools.detectionCentroidDistances(imageData);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Detection centroid distances 2D",
				"detectionCentroidDistances()"));
	}
	
}
