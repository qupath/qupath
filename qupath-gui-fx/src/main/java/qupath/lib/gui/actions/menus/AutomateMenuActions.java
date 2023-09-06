/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.actions.menus;

import org.controlsfx.control.action.Action;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.localization.QuPathResources;

import java.util.List;

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
		
		public final Action SCRIPT_EDITOR = qupath.getAutomateActions().SCRIPT_EDITOR;

		public final Action SCRIPT_INTERPRETER = qupath.getAutomateActions().SCRIPT_INTERPRETER;
		
		public final Action SEP_0 = ActionTools.createSeparator();
		
		public final Action HISTORY_SHOW = qupath.getAutomateActions().HISTORY_SHOW;

		public final Action HISTORY_SCRIPT = qupath.getAutomateActions().HISTORY_SCRIPT;

	}
	
}
