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
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

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
		var project = qupath.getProject();
		if (project == null) {
			DisplayHelpers.showErrorMessage(commandName, "No project open!");
			return;
		}
		// Try to get a project directory
		File dirBase = Projects.getBaseDirectory(project);
		
		// Prompt for where to save
		File fileOutput = qupath.getDialogHelper().promptToSaveFile(commandName, dirBase, null, "Text files", ".txt");
		if (fileOutput == null)
			return;
		
		// Write out image paths, along with metadata values
		Set<String> keys = new TreeSet<>();
		for (ProjectImageEntry<?> entry : qupath.getProject().getImageList()) {
			keys.addAll(entry.getMetadataKeys());
		}
		
		String delim = PathPrefs.getTableDelimiter();
		long startTime = System.currentTimeMillis();
		int n = 0;
		try (PrintWriter writer = new PrintWriter(fileOutput, StandardCharsets.UTF_8)) {
			writer.print("Name");
			writer.print(delim);
			writer.print("ID");
			writer.print(delim);
			writer.print("URIs");
			for (String key : keys) {
				writer.print(delim);
				writer.print(key);
			}
			writer.println();
			
			for (ProjectImageEntry<?> entry : project.getImageList()) {
				try {
					Collection<URI> uris = entry.getServerURIs();
					String path = String.join(" ", uris.stream().map(u -> u.toString()).collect(Collectors.toList()));
	//				String path = entry.getServerPath();
					writer.print(entry.getImageName());
					writer.print(delim);				
					writer.print(entry.getID());
					writer.print(delim);				
					writer.print(path);
					for (String key : keys) {
						writer.print(delim);
						String value = entry.getMetadataValue(key);
						if (value != null)
							writer.print(value);
					}
					writer.println();
					n++;
					logger.debug(path);
				} catch (IOException e) {
					logger.error("Error reading URIs from " + entry, e);
				}
			}		
		} catch (IOException e) {
			DisplayHelpers.showErrorMessage(commandName, fileOutput.getAbsolutePath() + " not found!");
		}
		long endTime = System.currentTimeMillis();
		logger.debug("Exported {} images in {} ms", n, endTime - startTime);
		
	}
	
	
}