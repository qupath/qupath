package qupath.lib.gui.actions.menus;

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.DefaultActions;
import qupath.lib.gui.actions.ActionTools.ActionAccelerator;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;

public class AutomateMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private DefaultActions defaultActions;
	
	private Actions actions;
	
	AutomateMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			this.defaultActions = qupath.getDefaultActions();
			actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}

	@Override
	public String getName() {
		return QuPathResources.getString("KEY:Menu.Automate.name");
	}

	@ActionMenu("KEY:Menu.Automate.name")
	public class Actions {
		
		@ActionDescription("KEY:Menu.Automate.name.scriptEditor")
		@ActionMenu("KEY:Menu.Automate.description.scriptEditor")
		@ActionAccelerator("shortcut+[")
		public final Action SCRIPT_EDITOR = createAction(() -> Commands.showScriptEditor(qupath));

		@ActionDescription("KEY:Menu.Automate.description.scriptInterpreter")
		@ActionMenu("KEY:Menu.Automate.name.scriptInterpreter")
		public final Action SCRIPT_INTERPRETER = createAction(() -> Commands.showScriptInterpreter(qupath));
		
		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionAccelerator("shortcut+shift+w")
		@ActionDescription("KEY:Menu.Automate.description.commandWorkflow")
		@ActionMenu("KEY:Menu.Automate.name.commandWorkflow")
		public final Action HISTORY_SHOW = Commands.createSingleStageAction(() -> Commands.createWorkflowDisplayDialog(qupath));

		@ActionDescription("KEY:Menu.Automate.description.commandScript")
		@ActionMenu("KEY:Menu.Automate.name.commandScript")
		public final Action HISTORY_SCRIPT = qupath.createImageDataAction(imageData -> Commands.showWorkflowScript(qupath, imageData));

	}
	
}
