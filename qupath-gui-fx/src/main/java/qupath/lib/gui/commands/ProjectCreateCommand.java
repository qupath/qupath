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

import java.awt.image.BufferedImage;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.projects.Projects;

/**
 * Command to create a new (empty) project.
 * 
 * @author Pete Bankhead
 *
 */
public class ProjectCreateCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(ProjectCreateCommand.class);

	private QuPathGUI qupath;
	
	public ProjectCreateCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		File dir = qupath.getDialogHelper().promptForDirectory(null);
		if (dir == null)
			return;
		if (!dir.isDirectory()) {
			logger.error(dir + " is not a valid project directory!");
		}
		for (File f : dir.listFiles()) {
			if (!f.isHidden()) {
				logger.error("Cannot create project for non-empty directory {}", dir);
				DisplayHelpers.showErrorMessage("Project creator", "Project directory must be empty!");
				return;
			}
		}
		qupath.setProject(Projects.createProject(dir, BufferedImage.class));
	}
	
}