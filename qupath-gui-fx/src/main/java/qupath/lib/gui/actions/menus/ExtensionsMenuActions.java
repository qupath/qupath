package qupath.lib.gui.actions.menus;

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;

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
		return QuPathResources.getString("KEY:Menu.Extensions.name");
	}

	
	@ActionMenu("KEY:Menu.Extensions.name")
	public class Actions {
		
		@ActionDescription("KEY:Menu.Extensions.description.installed")
		@ActionMenu("KEY:Menu.Extensions.name.installed")
		public final Action EXTENSIONS = createAction(() -> Commands.showInstalledExtensions(qupath));

		@ActionMenu("")
		public final Action SEP_1 = ActionTools.createSeparator();

	}

}
