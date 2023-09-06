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

package qupath.lib.gui.actions;

import org.controlsfx.control.action.Action;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionIcon;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.tools.IconFactory;

import static qupath.lib.gui.actions.ActionTools.createAction;

/**
 * Default actions associated with QuPath's 'Automate' (scripting) menu.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class AutomateActions {

	@ActionAccelerator("shortcut+[")
	@ActionConfig("Action.Automate.scriptEditor")
	@ActionIcon(IconFactory.PathIcons.SCRIPT_EDITOR)
	public final Action SCRIPT_EDITOR;

	@ActionConfig("Action.Automate.scriptInterpreter")
	public final Action SCRIPT_INTERPRETER;

	@ActionAccelerator("shortcut+shift+w")
	@ActionConfig("Action.Automate.commandWorkflow")
	public final Action HISTORY_SHOW;

	@ActionConfig("Action.Automate.commandScript")
	public final Action HISTORY_SCRIPT;


	private QuPathGUI qupath;

	public AutomateActions(QuPathGUI qupath) {
		this.qupath = qupath;

		SCRIPT_EDITOR = createAction(() -> Commands.showScriptEditor(qupath));

		SCRIPT_INTERPRETER = createAction(() -> Commands.showScriptInterpreter(qupath));

		HISTORY_SHOW = Commands.createSingleStageAction(() -> Commands.createWorkflowDisplayDialog(qupath));

		HISTORY_SCRIPT = qupath.createImageDataAction(imageData -> Commands.showWorkflowScript(qupath, imageData));

		// This has the effect of applying the annotations
		ActionTools.getAnnotatedActions(this);
	}
	
}