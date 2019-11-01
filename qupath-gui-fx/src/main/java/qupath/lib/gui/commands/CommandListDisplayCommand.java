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

import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.tools.CommandFinderTools;

/**
 * Action to display a command viewer, loosely based on ImageJ/Fiji's 'Command Finder'.
 * <p>
 * This is a more basic implementation than in ImageJ's case, which simply replicates the items within
 * the menu to provide a fast-filterable way to choose or discover commands without needing to navigate the menus.
 * 
 * @author Pete Bankhead
 *
 */
public class CommandListDisplayCommand implements PathCommand {

	private QuPathGUI qupath;
	
	private Stage dialog;
	
	public CommandListDisplayCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (dialog != null && dialog.isShowing()) {
			dialog.toFront();
		} else {
			dialog = CommandFinderTools.createCommandFinderDialog(qupath);
		}
		dialog.show();
		qupath.getMenuBar().setUseSystemMenuBar(true);
//		textField.requestFocus();
	}
		
}