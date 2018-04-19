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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Command to export image paths &amp; metadata from a current project.
 * 
 * @author Pete Bankhead
 */
public class ProjectExportImageListCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(ProjectExportImageListCommand.class);
	
	private static final String commandName = "Project: Export image list";

	private QuPathGUI qupath;
	
	public ProjectExportImageListCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		if (qupath.getProject() == null) {
			DisplayHelpers.showErrorMessage(commandName, "No project open!");
			return;
		}
		
		File fileOutput = qupath.getDialogHelper().promptToSaveFile(commandName, null, null, "Text files", ".txt");
		if (fileOutput == null)
			return;
		
		// Write out image paths, along with metadata values
		Set<String> keys = new TreeSet<>();
		for (ProjectImageEntry<?> entry : qupath.getProject().getImageList()) {
			keys.addAll(entry.getMetadataKeys());
		}
		
		try (PrintWriter writer = new PrintWriter(fileOutput)) {
			writer.print("Path");
			writer.print(PathPrefs.getTableDelimiter());
			writer.print("Name");
			for (String key : keys) {
				writer.print(PathPrefs.getTableDelimiter());
				writer.print(key);
			}
			writer.println();
			
			for (ProjectImageEntry<?> entry : qupath.getProject().getImageList()) {
				String path = entry.getStoredServerPath();
				writer.print(path);
				writer.print(PathPrefs.getTableDelimiter());				
				writer.print(entry.getImageName());
				for (String key : keys) {
					writer.print(PathPrefs.getTableDelimiter());
					String value = entry.getMetadataValue(key);
					if (value != null)
						writer.print(value);
				}
				writer.println();
				logger.info(path);
			}		
		} catch (FileNotFoundException e) {
			DisplayHelpers.showErrorMessage(commandName, fileOutput.getAbsolutePath() + " not found!");
		}
		
		
	}
	
	
}