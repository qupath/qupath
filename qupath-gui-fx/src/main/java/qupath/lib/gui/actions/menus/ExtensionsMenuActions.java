package qupath.lib.gui.actions.menus;

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.localization.QuPathResources;

public class ExtensionsMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	
	private Actions actions;
	
	ExtensionsMenuActions(QuPathGUI qupath) {
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
		return QuPathResources.getString("Menu.Extensions");
	}

	
	@ActionMenu("Menu.Extensions")
	public class Actions {
		
		@ActionConfig("Action.Extensions.installed")
		public final Action EXTENSIONS = createAction(() -> Commands.showInstalledExtensions(qupath));

		@ActionMenu("")
		public final Action SEP_1 = ActionTools.createSeparator();

	}

}
