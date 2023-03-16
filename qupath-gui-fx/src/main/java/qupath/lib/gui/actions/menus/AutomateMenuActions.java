package qupath.lib.gui.actions.menus;

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.localization.QuPathResources;

public class AutomateMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	
	private Actions actions;
	
	AutomateMenuActions(QuPathGUI qupath) {
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
		return QuPathResources.getString("Menu.Automate");
	}

	@ActionMenu("Menu.Automate")
	public class Actions {
		
		@ActionAccelerator("shortcut+[")
		@ActionConfig("Action.Automate.scriptEditor")
		public final Action SCRIPT_EDITOR = createAction(() -> Commands.showScriptEditor(qupath));

		@ActionConfig("Action.Automate.scriptInterpreter")
		public final Action SCRIPT_INTERPRETER = createAction(() -> Commands.showScriptInterpreter(qupath));
		
		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionAccelerator("shortcut+shift+w")
		@ActionConfig("Action.Automate.commandWorkflow")
		public final Action HISTORY_SHOW = Commands.createSingleStageAction(() -> Commands.createWorkflowDisplayDialog(qupath));

		@ActionConfig("Action.Automate.commandScript")
		public final Action HISTORY_SCRIPT = qupath.createImageDataAction(imageData -> Commands.showWorkflowScript(qupath, imageData));

	}
	
}
