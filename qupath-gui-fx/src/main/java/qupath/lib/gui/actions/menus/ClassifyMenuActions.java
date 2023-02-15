package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.objects.PathDetectionObject;

public class ClassifyMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	
	private Actions actions;
	
	ClassifyMenuActions(QuPathGUI qupath) {
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
		return QuPathResources.getString("KEY:Menu.Classify.name");
	}
	
	@ActionMenu("KEY:Menu.Classify.name")
	public class Actions {
				
		@ActionMenu("KEY:Menu.Classify.Objects.name")
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionDescription("KEY:Menu.Classify.Objects.name.resetDetectionClassifications")
		@ActionMenu("KEY:Menu.Classify.Objects.description.resetDetectionClassifications")
		public final Action RESET_DETECTION_CLASSIFICATIONS = qupath.createImageDataAction(imageData -> Commands.resetClassifications(imageData, PathDetectionObject.class));

		@ActionMenu("KEY:Menu.Classify.Pixels.name")
		public final Action SEP_3 = ActionTools.createSeparator();

		public final Action SEP_4 = ActionTools.createSeparator();

	}

}
