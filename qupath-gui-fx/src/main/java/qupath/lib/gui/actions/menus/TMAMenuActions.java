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

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.TMACommands;
import qupath.lib.gui.localization.QuPathResources;
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
		return QuPathResources.getString("Menu.TMA.name");
	}
	

	@ActionMenu("Menu.TMA")
	public class Actions {
		
		@ActionConfig("Action.TMA.specifyGrid")
		public final Action CREATE_MANUAL = qupath.createImageDataAction(imageData -> TMACommands.promptToCreateTMAGrid(imageData));

		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionMenu("Menu.TMA.add")
		public final AddActions addActions = new AddActions();
		
		@ActionMenu("Menu.TMA.remove")
		public final RemoveActions removeActions = new RemoveActions();

		@ActionConfig("Action.TMA.relabel")
		public final Action RELABEL = qupath.createImageDataAction(imageData -> TMACommands.promptToRelabelTMAGrid(imageData));
		
		@ActionConfig("Action.TMA.resetMetadata")
		public final Action RESET_METADATA = qupath.createImageDataAction(imageData -> Commands.resetTMAMetadata(imageData));
		
		@ActionConfig("Action.TMA.deleteGrid")
		public final Action CLEAR_CORES = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, TMACoreObject.class));
		
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionConfig("Action.TMA.findHull")
		public final Action CONVEX_HULL = qupath.createPluginAction("Find convex hull detections (TMA)", FindConvexHullDetectionsPlugin.class, null);

		
		public class AddActions {
			
			@ActionConfig("Action.TMA.addRowAbove")
			public final Action ADD_ROW_BEFORE = qupath.createImageDataAction(imageData -> TMACommands.promptToAddRowBeforeSelected(imageData));
			
			@ActionConfig("Action.TMA.addRowBelow")
			public final Action ADD_ROW_AFTER = qupath.createImageDataAction(imageData -> TMACommands.promptToAddRowAfterSelected(imageData));
			
			@ActionConfig("Action.TMA.addColumnBefore")
			public final Action ADD_COLUMN_BEFORE = qupath.createImageDataAction(imageData -> TMACommands.promptToAddColumnBeforeSelected(imageData));
			
			@ActionConfig("Action.TMA.addColumnAfter")
			public final Action ADD_COLUMN_AFTER = qupath.createImageDataAction(imageData -> TMACommands.promptToAddColumnAfterSelected(imageData));
			
		}
		
		public class RemoveActions {
			
			@ActionConfig("Action.TMA.removeRow")
			public final Action REMOVE_ROW = qupath.createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridRow(imageData));
			
			@ActionConfig("Action.TMA.removeColumn")
			public final Action REMOVE_COLUMN = qupath.createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridColumn(imageData));
			
		}

	}

}
