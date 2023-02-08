package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.TMACommands;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.objects.FindConvexHullDetectionsPlugin;

public class TMAMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	
	private Actions actions;
	
	TMAMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}
	
	@Override
	public String getName() {
		return QuPathResources.getString("KEY:Menu.TMA.name");
	}
	

	@ActionMenu("KEY:Menu.TMA.name")
	public class Actions {
		
		@ActionDescription("Create a manual TMA grid, by defining labels and the core diameter. "
				+ "This can optionally be restricted to a rectangular annotation.")
		@ActionMenu("Specify TMA grid")
		public final Action CREATE_MANUAL = qupath.createImageDataAction(imageData -> TMACommands.promptToCreateTMAGrid(imageData));

		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionDescription("Add a row to the TMA grid before (above) the row containing the current selected object.")
		@ActionMenu("Add...>Add TMA row before")
		public final Action ADD_ROW_BEFORE = qupath.createImageDataAction(imageData -> TMACommands.promptToAddRowBeforeSelected(imageData));
		
		@ActionDescription("Add a row to the TMA grid after (below) the row containing the current selected object.")
		@ActionMenu("Add...>Add TMA row after")
		public final Action ADD_ROW_AFTER = qupath.createImageDataAction(imageData -> TMACommands.promptToAddRowAfterSelected(imageData));
		
		@ActionDescription("Add a column to the TMA grid before (to the left of) the column containing the current selected object.")
		@ActionMenu("Add...>Add TMA column before")
		public final Action ADD_COLUMN_BEFORE = qupath.createImageDataAction(imageData -> TMACommands.promptToAddColumnBeforeSelected(imageData));
		
		@ActionDescription("Add a column to the TMA grid after (to the right of) the column containing the current selected object.")
		@ActionMenu("Add...>Add TMA column after")
		public final Action ADD_COLUMN_AFTER = qupath.createImageDataAction(imageData -> TMACommands.promptToAddColumnAfterSelected(imageData));
		
		@ActionDescription("Remove the row containing the current selected object from the TMA grid.")
		@ActionMenu("Remove...>Remove TMA row")
		public final Action REMOVE_ROW = qupath.createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridRow(imageData));
		
		@ActionDescription("Remove the column containing the current selected object from the TMA grid.")
		@ActionMenu("Remove...>Remove TMA column")
		public final Action REMOVE_COLUMN = qupath.createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridColumn(imageData));

		@ActionDescription("Relabel the cores of a TMA grid. This is often needed after adding or deleting rows or columns.")
		@ActionMenu("Relabel TMA grid")
		public final Action RELABEL = qupath.createImageDataAction(imageData -> TMACommands.promptToRelabelTMAGrid(imageData));
		
		@ActionDescription("Remove all the metadata for the TMA grid in the current image.")
		@ActionMenu("Reset TMA metadata")
		public final Action RESET_METADATA = qupath.createImageDataAction(imageData -> Commands.resetTMAMetadata(imageData));
		
		@ActionDescription("Delete the TMA grid for the current image.")
		@ActionMenu("Delete TMA grid")
		public final Action CLEAR_CORES = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, TMACoreObject.class));
		
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionDescription("Find all detections occurring on the convex hull of the detections within a TMA core. "
				+ "This can be used to find cells occurring towards the edge of the core, which can then be deleted if necessary. "
				+ "Often these cells may yield less reliable measurements because of artifacts.")
		@ActionMenu("Find convex hull detections (TMA)")
		public final Action CONVEX_HULL = qupath.createPluginAction("Find convex hull detections (TMA)", FindConvexHullDetectionsPlugin.class, null);

	}

}
