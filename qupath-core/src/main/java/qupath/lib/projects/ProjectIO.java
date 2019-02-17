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

package qupath.lib.projects;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Read/write QuPath projects.
 * 
 * @author Pete Bankhead
 *
 */
public class ProjectIO {

	final private static Logger logger = LoggerFactory.getLogger(ProjectIO.class);
	
	private static final String DEFAULT_PROJECT_NAME = "project";
	
	private static final String DEFAULT_PROJECT_EXTENSION = "qpproj";

	/**
	 * Read project from file.
	 * 
	 * @param fileProject
	 * @param cls
	 * @return
	 */
	public static <T> Project<T> loadProject(final File fileProject, final Class<T> cls) {
		if (fileProject == null) {
			return null;
		}
		
		try (Reader fileReader = new BufferedReader(new FileReader(fileProject))){
			Gson gson = new Gson();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);
			// Didn't have the foresight to add a version number from the start...
			if (!element.has("version")) {
				return LegacyProject.readFromFile(fileProject, cls);
			}
			return (Project<T>)DefaultProject.loadFromFile(fileProject);
		} catch (Exception e) {
			logger.error("Error loading project", e);
			return null;
		}
	}
	
	
	/**
	 * Deprecated in favor of simply calling {@code project.syncProject()}.
	 * 
	 * @param project
	 */
	@Deprecated
	public static void writeProject(final Project<?> project) {
		try {
			project.syncChanges();
		} catch (Exception e) {
			logger.error("Error writing project", e);
		}
	}


	/**
	 * Get a suitable project file.
	 * 
	 * Note: This behavior has changed from <= v0.1.2.
	 * The earlier code often ignored the <code>name</code>.
	 * 
	 * @param project
	 * @param name
	 * @return
	 */
	static File getProjectFile(final Project<?> project, String name) {
		if (project == null)
			return null;
		
		// Use default name
		if (name == null || name.length() == 0) {
			// If we already have a file, use that
			if (project.getFile() != null && (project.getFile() != project.getBaseDirectory()))
				return project.getFile();
			// Default to project.qpproj
			name = DEFAULT_PROJECT_NAME;
		}

		// We need a base directory
		File dirBase = project.getBaseDirectory();
		if (dirBase == null || !dirBase.isDirectory()) {
			logger.warn("No base directory for project!");
			return null;
		}
		
		// Return File with extension
		if (!name.endsWith("."))
			name += ".";
		return new File(dirBase, name + ProjectIO.getProjectExtension());
	}


	/**
	 * Get the default extension for a QuPath project file.
	 * 
	 * @return qpproj
	 */
	public static String getProjectExtension() {
		return DEFAULT_PROJECT_EXTENSION;
	}




}
