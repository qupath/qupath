package qupath.lib.gui.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.DistanceTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * New command to get the distance from cells to annotations.
 * <p>
 * Note that this is subject to change! This is currently not scriptable and may be better 
 * as a plugin rather than command.
 * 
 * @author Pete Bankhead
 *
 */
public class DistanceToAnnotationsCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(DistanceToAnnotationsCommand.class);
	
	private QuPathGUI qupath;
	
	public DistanceToAnnotationsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		var imageData = qupath.getImageData();
		if (imageData == null)
			return;
		
		if (imageData.getServer().nZSlices() > 1) {
			logger.debug("Warning user that measurements will be 2D...");
			if (!DisplayHelpers.showConfirmDialog("Distance to annotations 2D", 
					"Distance to annotations command works only in 2D - distances will not be calculated for objects on different z-slices or time-points")) {
				logger.debug("Command cancelled");
				return;
			}
		}
		
		DistanceTools.detectionToAnnotationDistances(imageData);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Distance to annotations 2D",
				"detectionToAnnotationDistances()"));
	}
	
}
