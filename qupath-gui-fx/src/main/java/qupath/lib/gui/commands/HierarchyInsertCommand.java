package qupath.lib.gui.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;

/**
 * Command to insert annotations where they 'should be' within an object hierarchy.
 * 
 * @author Pete Bankhead
 */
public class HierarchyInsertCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	public HierarchyInsertCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var imageData = qupath.getImageData();
		var hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null)
			return;
		hierarchy.insertPathObjects(hierarchy.getSelectionModel().getSelectedObjects());
	}

}
