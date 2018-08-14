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

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.projects.ProjectIO;

/**
 * Command to manually save a project.
 * 
 * This isn't generally needed, as projects should take care of their own saving
 * when relevant properties change.
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
public class ProjectSaveCommand implements PathCommand {

	private QuPathGUI qupath;
	
	public ProjectSaveCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		ProjectIO.writeProject(qupath.getProject(),
				message -> DisplayHelpers.showErrorMessage("Error", message));
	}
	
}