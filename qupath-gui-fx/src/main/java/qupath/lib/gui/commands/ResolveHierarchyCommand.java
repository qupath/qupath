package qupath.lib.gui.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to resolve parent-child relationships within the object hierarchy.
 * 
 * @author Pete Bankhead
 */
public class ResolveHierarchyCommand implements PathCommand {
	
	private static final String NAME = "Resolve hierarchy";
	
	private QuPathGUI qupath;
	
	public ResolveHierarchyCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var imageData = qupath.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError(NAME);
		}
		var hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null)
			return;
		
		if (!Dialogs.showConfirmDialog(NAME,
				"Are you sure you want to resolve object relationships?\n" +
				"For large object hierarchies this can take a long time.")) {
			return;
		}
		hierarchy.resolveHierarchy();
		
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Resolve hierarchy",
				"resolveHierarchy()"));
	}

}
