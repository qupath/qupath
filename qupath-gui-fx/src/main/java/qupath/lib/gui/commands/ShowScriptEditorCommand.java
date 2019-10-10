/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.commands;

import java.awt.Window;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.panels.WorkflowCommandLogView;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.images.ImageData;

/**
 * Command to show the script editor associated with a QuPathGUI.
 * 
 * @author Pete Bankhead
 *
 */
public class ShowScriptEditorCommand implements PathCommand {
	
	private QuPathGUI qupath;
	private boolean showWorkflowScript;
	
	public ShowScriptEditorCommand(final QuPathGUI qupath, final boolean showWorkflowScript) {
		this.qupath = qupath;
		this.showWorkflowScript = showWorkflowScript;
	}

	@Override
	public void run() {
		ScriptEditor scriptEditor = qupath.getScriptEditor();

		// Try to show script from workflow, if possible
		if (showWorkflowScript) {
			ImageData<?> imageData = qupath.getImageData();
			if (imageData != null) {
				WorkflowCommandLogView.showScript(qupath.getScriptEditor(), imageData.getHistoryWorkflow());
				return;
			}
		}
		
		// Show script editor with a new script
		if ((scriptEditor instanceof Window) && ((Window)scriptEditor).isShowing())
			((Window)scriptEditor).toFront();
		else
			scriptEditor.showEditor();
	}


}