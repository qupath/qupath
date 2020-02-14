package qupath.lib.gui.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to merge point annotations.
 * 
 * @author Pete Bankhead
 *
 */
public class MergePointsCommand implements PathCommand {

	private QuPathGUI qupath;
	private boolean selectedOnly;

	public MergePointsCommand(final QuPathGUI qupath, final boolean selectedOnly) {
		this.qupath = qupath;
		this.selectedOnly = selectedOnly;
	}

	@Override
	public void run() {
		var imageData = qupath.getImageData();
		var hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null) {
			Dialogs.showErrorNotification("Merge points", "No image selected - cannot merge points!");
			return;
		}
		if (selectedOnly) {
			PathObjectTools.mergePointsForSelectedObjectClasses(hierarchy);
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					"Merge points for selected classifications",
					"mergePointsForSelectedObjectClasses();"
					));
		} else {
			PathObjectTools.mergePointsForAllClasses(hierarchy);
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
					"Merge points for all classifications",
					"mergePointsForAllClasses();"
					));
		}
	}


}